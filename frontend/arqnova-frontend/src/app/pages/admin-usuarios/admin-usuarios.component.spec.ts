import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AdminUsersService, AdminUser } from '../../admin/admin-users.service';
import { BASE_PATH } from '../../api/variables';
import { AuthService } from '../../auth/auth.service';
import { AdminUsuariosComponent } from './admin-usuarios.component';

describe('AdminUsuariosComponent', () => {
  let component: AdminUsuariosComponent;
  let service: Record<string, ReturnType<typeof vi.fn>>;
  const user: AdminUser = {
    id: 'u-1', username: 'ana@arqnova.test', nombreCompleto: 'Ana ArqNova',
    rol: 'SOLICITANTE', departamento: 'Sistemas', habilitado: true
  };

  beforeEach(() => {
    service = {
      listarUsuarios: vi.fn(() => of([user])),
      listarDepartamentos: vi.fn(() => of(['Sistemas'])),
      crearUsuario: vi.fn(() => of(user)),
      actualizarUsuario: vi.fn(() => of(user)),
      actualizarEstado: vi.fn(() => of({ ...user, habilitado: false })),
      eliminarUsuario: vi.fn(), subirAvatar: vi.fn()
    };
    TestBed.configureTestingModule({
      imports: [AdminUsuariosComponent],
      providers: [
        { provide: AdminUsersService, useValue: service },
        { provide: AuthService, useValue: { currentUser: () => null, updateCurrentUser: vi.fn() } },
        { provide: BASE_PATH, useValue: 'http://localhost:8080' }
      ]
    });
    component = TestBed.createComponent(AdminUsuariosComponent).componentInstance;
    component.ngOnInit();
  });

  it('lista usuarios mostrando solo roles actuales', () => {
    expect(service['listarUsuarios']).toHaveBeenCalledOnce();
    expect(component.usuarios()).toEqual([user]);
    expect(component.rolVisible('SOLICITANTE')).toBe('Usuario/Colaborador');
    expect(component.rolVisible('REVISOR')).toBe('Usuario/Colaborador');
    expect(component.rolVisible('ADMINISTRADOR')).toBe('Administrador');
    expect(component.roleOptions).toEqual([
      { label: 'Administrador', value: 'ADMINISTRADOR' },
      { label: 'Usuario/Colaborador', value: 'SOLICITANTE' }
    ]);
  });

  it('crea un usuario con la compatibilidad interna vigente', () => {
    component.abrirModalCrear();
    component.form.setValue({
      username: 'ana@arqnova.test', password: 'Test1234', nombreCompleto: 'Ana ArqNova', rol: 'SOLICITANTE'
    });
    component.guardarUsuario();

    expect(service['crearUsuario']).toHaveBeenCalledWith(expect.objectContaining({
      username: 'ana@arqnova.test', rol: 'SOLICITANTE'
    }));
  });

  it('edita un usuario sin exigir una nueva contraseña', () => {
    component.iniciarEdicion(user);
    component.form.controls.nombreCompleto.setValue('Ana Actualizada');
    component.guardarUsuario();

    expect(service['actualizarUsuario']).toHaveBeenCalledWith('u-1', expect.objectContaining({
      nombreCompleto: 'Ana Actualizada', password: undefined
    }));
  });

  it('cambia el estado activo sin alterar el rol', () => {
    component.solicitarCambioEstado(user);
    component.confirmarCambioEstado();

    expect(service['actualizarEstado']).toHaveBeenCalledWith('u-1', false);
    expect(component.usuarios()[0].habilitado).toBe(false);
    expect(component.usuarios()[0].rol).toBe('SOLICITANTE');
  });
});
