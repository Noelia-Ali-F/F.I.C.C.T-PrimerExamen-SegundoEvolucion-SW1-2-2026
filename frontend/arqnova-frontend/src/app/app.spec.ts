import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { appConfig } from './app.config';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('App', () => {
  beforeEach(async () => {
    localStorage.setItem('WORKFLOW_SYS_USER', JSON.stringify({ username: 'test', rol: 'ADMINISTRADOR', departamento: 'Sistemas' }));
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: (query: string) => ({ matches: false, media: query, onchange: null, addListener: () => {}, removeListener: () => {}, addEventListener: () => {}, removeEventListener: () => {}, dispatchEvent: () => false })
    });
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [...appConfig.providers, provideHttpClientTesting()],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('muestra únicamente los actores oficiales sin cambiar los roles internos', () => {
    const app = TestBed.createComponent(App).componentInstance;
    expect(app.visibleRole('ADMINISTRADOR')).toBe('Administrador');
    expect(app.visibleRole('SOLICITANTE')).toBe('Usuario/Colaborador');
    expect(app.visibleRole('REVISOR')).toBe('Usuario/Colaborador');
  });

});
