import { Component, inject, signal } from '@angular/core';
import { form, FormField, submit, required } from '@angular/forms/signals';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormField, MatIconModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  loginModel = signal({
    username: '',
    password: ''
  });

  loginForm = form(this.loginModel, (s) => {
    required(s.username);
    required(s.password);
  });

  isSubmitting = false;
  errorMsg = '';
  successMsg = '';
  showPassword = signal(false);

  constructor() {
    this.successMsg = this.route.snapshot.queryParamMap.get('registered') === '1'
      ? 'Cuenta creada correctamente. Ya puedes iniciar sesión.' : '';
    const reason = this.route.snapshot.queryParamMap.get('reason');
    if (reason === 'expired') this.errorMsg = 'Tu sesión expiró. Inicia sesión nuevamente para continuar.';
    if (reason === 'disabled') this.errorMsg = 'Tu cuenta se encuentra deshabilitada. Contacta al administrador.';
  }

  onSubmit() {
    if (this.isSubmitting) return;
    submit(this.loginForm, async () => {
      this.isSubmitting = true;
      this.errorMsg = '';

      const credentials = {
        username: (this.loginModel().username || '').trim(),
        password: this.loginModel().password || ''
      };

      if (!credentials.username) { this.isSubmitting = false; this.errorMsg = 'Correo electrónico obligatorio.'; return; }
      if (!credentials.password) { this.isSubmitting = false; this.errorMsg = 'Contraseña obligatoria.'; return; }
      if (credentials.username.includes('@') && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(credentials.username)) {
        this.isSubmitting = false; this.errorMsg = 'Ingresa un correo electrónico válido.'; return;
      }

      this.authService.login(credentials).subscribe({
        next: () => {
          this.isSubmitting = false;
          const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
          const safeReturnUrl = returnUrl && returnUrl.startsWith('/') && !returnUrl.startsWith('//') ? returnUrl : '/';
          this.router.navigateByUrl(safeReturnUrl);
        },
        error: (err) => {
          this.isSubmitting = false;
          if (err?.status === 0) this.errorMsg = 'No se pudo conectar con el servidor. Intenta nuevamente.';
          else if (err?.error?.mensaje?.toLowerCase().includes('deshabilitada')) this.errorMsg = 'Tu cuenta se encuentra deshabilitada.';
          else if (err?.status === 429) this.errorMsg = 'Se realizaron demasiados intentos. Espera un momento antes de reintentar.';
          else if (err?.status >= 500) this.errorMsg = 'ArqNova no está disponible temporalmente. Intenta nuevamente más tarde.';
          else this.errorMsg = 'Correo o contraseña incorrectos.';
        }
      });
    });
  }

  showRecoveryPending() {
    this.errorMsg = 'La recuperación de contraseña todavía no está disponible. Contacta al administrador.';
  }
}
