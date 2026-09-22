import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { firstValueFrom, of } from 'rxjs';
import { AuthService } from './auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  it('redirige al login cuando no existe una sesión autenticada', async () => {
    const loginTree = { redirected: true };
    const auth = { currentUser: () => null, ensureSession: vi.fn(() => of(false)) };
    const router = { createUrlTree: vi.fn(() => loginTree) };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router }
      ]
    });

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url: '/proyectos' } as never)
    );
    const resolved = typeof result === 'boolean' ? result : await firstValueFrom(result as any);

    expect(resolved).toBe(loginTree);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/proyectos' }
    });
  });

  it('valida nuevamente una sesión cargada antes de abrir una ruta protegida', async () => {
    const auth = { currentUser: () => ({ username: 'usuario' }), ensureSession: vi.fn(() => of(true)) };
    const router = { createUrlTree: vi.fn() };
    TestBed.configureTestingModule({ providers: [
      { provide: AuthService, useValue: auth },
      { provide: Router, useValue: router }
    ] });

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url: '/proyectos' } as never)
    );

    expect(await firstValueFrom(result as any)).toBe(true);
    expect(auth.ensureSession).toHaveBeenCalledOnce();
  });
});
