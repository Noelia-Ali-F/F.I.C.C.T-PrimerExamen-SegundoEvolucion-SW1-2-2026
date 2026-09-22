import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';
import { catchError, throwError } from 'rxjs';
import { Router } from '@angular/router';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const user = authService.currentUser();

  if (user) {
    let headersConfig: Record<string, string> = {
      Authorization: `Bearer ${user.token}`
    };

    if (user.username) headersConfig['X-Usuario'] = user.username;
    if (user.rol) headersConfig['X-Rol'] = user.rol;
    if (user.departamento) headersConfig['X-Departamento'] = user.departamento;

    const cloned = req.clone({
      setHeaders: headersConfig
    });
    return next(cloned).pipe(catchError(error => {
      const disabled = error?.status === 403 && String(error?.error?.mensaje || '').toLowerCase().includes('deshabilitada');
      if (error?.status === 401 || disabled) {
        authService.clearSession();
        router.navigate(['/login'], {
          replaceUrl: true,
          queryParams: {
            reason: disabled ? 'disabled' : 'expired',
            returnUrl: router.url.startsWith('/') ? router.url : '/proyectos'
          }
        });
      }
      return throwError(() => error);
    }));
  }

  return next(req);
};
