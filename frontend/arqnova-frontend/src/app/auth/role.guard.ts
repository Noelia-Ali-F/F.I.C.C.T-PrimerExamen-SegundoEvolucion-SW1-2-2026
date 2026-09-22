import { inject } from '@angular/core';
import { Router, type CanActivateFn } from '@angular/router';
import { AuthService, UserContext } from './auth.service';
import { map } from 'rxjs';

export const roleGuard: CanActivateFn = (route) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const allowedRoles = route.data?.['roles'] as Array<UserContext['rol']> | undefined;
  if (!allowedRoles || allowedRoles.length === 0) {
    return true;
  }

  return authService.ensureSession().pipe(
    map(valid => valid && authService.hasAnyRole(allowedRoles) ? true : router.createUrlTree(['/']))
  );
};
