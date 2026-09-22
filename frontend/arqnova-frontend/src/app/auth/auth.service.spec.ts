import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { AutenticacinService } from '../api/api/autenticacin.service';
import { BASE_PATH } from '../api/variables';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let api: { login: ReturnType<typeof vi.fn>; register: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    localStorage.clear();
    api = { login: vi.fn(), register: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        AuthService,
        { provide: AutenticacinService, useValue: api },
        { provide: BASE_PATH, useValue: 'http://localhost:8080' }
      ]
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => { http.verify(); localStorage.clear(); });

  it('inicia sesión y conserva el contexto devuelto por ArqNova', () => {
    api.login.mockReturnValue(of({
      exito: true,
      datos: { username: 'usuario@arqnova.test', nombreCompleto: 'Usuario ArqNova', rol: 'SOLICITANTE', departamento: 'Sistemas', token: 'token' }
    }));

    service.login({ username: ' usuario@arqnova.test ', password: 'Test1234' }).subscribe(result => {
      expect(result.username).toBe('usuario@arqnova.test');
      expect(service.currentUser()?.nombreCompleto).toBe('Usuario ArqNova');
      expect(localStorage.getItem('ARQNOVA_USER')).toContain('usuario@arqnova.test');
    });
    expect(api.login).toHaveBeenCalledWith({ username: 'usuario@arqnova.test', password: 'Test1234' });
  });

  it('propaga un login incorrecto sin crear sesión', () => {
    api.login.mockReturnValue(throwError(() => ({ status: 401 })));

    service.login({ username: 'usuario@arqnova.test', password: 'incorrecta' }).subscribe({
      error: error => expect(error.status).toBe(401)
    });
    expect(service.currentUser()).toBeNull();
    expect(localStorage.getItem('ARQNOVA_USER')).toBeNull();
  });

  it('no altera espacios significativos de la contraseña', () => {
    api.login.mockReturnValue(of({ exito: true, datos: {
      username: 'usuario@arqnova.test', rol: 'SOLICITANTE', departamento: 'Sistemas', token: 'token'
    } }));

    service.login({ username: ' usuario@arqnova.test ', password: ' clave con espacios ' }).subscribe();

    expect(api.login).toHaveBeenCalledWith({
      username: 'usuario@arqnova.test', password: ' clave con espacios '
    });
  });

  it('registra con el contrato interno compatible sin exponer roles legacy en UI', () => {
    api.register.mockReturnValue(of({ exito: true, datos: {} }));
    service.register({ email: ' NUEVO@ARQNOVA.TEST ', password: 'Test1234', nombreCompleto: ' Nuevo Usuario ' }).subscribe();

    expect(api.register).toHaveBeenCalledWith({
      username: 'nuevo@arqnova.test',
      password: 'Test1234',
      nombreCompleto: 'Nuevo Usuario',
      rol: 'SOLICITANTE',
      departamento: 'Sistemas'
    });
  });

  it('descarta una sesión expirada antes de consultar el backend', () => {
    localStorage.setItem('ARQNOVA_USER', JSON.stringify({
      username: 'usuario@arqnova.test', rol: 'SOLICITANTE', departamento: 'Sistemas', token: 'a.b.c.1.e'
    }));
    const restored = TestBed.runInInjectionContext(() => new AuthService());

    restored.ensureSession().subscribe(valid => expect(valid).toBe(false));
    expect(localStorage.getItem('ARQNOVA_USER')).toBeNull();
  });

  it('vuelve a comprobar la expiración de una sesión ya cargada', () => {
    service.currentUser.set({
      username: 'usuario@arqnova.test', rol: 'SOLICITANTE', departamento: 'Sistemas', token: 'a.b.c.1.e'
    });
    service.sessionReady.set(true);

    service.ensureSession().subscribe(valid => expect(valid).toBe(false));

    expect(service.currentUser()).toBeNull();
  });

  it('cierra la sesión local aunque el backend no esté disponible', () => {
    const token = `a.b.c.${Math.floor(Date.now() / 1000) + 3600}.e`;
    service.currentUser.set({
      username: 'usuario@arqnova.test', rol: 'SOLICITANTE', departamento: 'Sistemas', token
    });
    localStorage.setItem('ARQNOVA_USER', JSON.stringify(service.currentUser()));

    service.logout().subscribe();
    http.expectOne('http://localhost:8080/api/v1/auth/logout').flush({}, { status: 503, statusText: 'Unavailable' });

    expect(localStorage.getItem('ARQNOVA_USER')).toBeNull();
  });
});
