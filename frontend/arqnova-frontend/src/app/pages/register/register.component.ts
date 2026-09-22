import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-register', standalone: true,
  imports: [ReactiveFormsModule, MatIconModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrls: ['../login/login.component.css', './register.component.css']
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  submitting = signal(false);
  showPassword = signal(false);
  showConfirmation = signal(false);
  error = signal('');

  form = this.fb.nonNullable.group({
    nombreCompleto: ['', [Validators.required, Validators.minLength(3)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    confirmation: ['', Validators.required]
  });

  submit() {
    if (this.submitting()) return;
    this.error.set('');
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const value = this.form.getRawValue();
    if (!value.nombreCompleto.trim()) { this.error.set('El nombre completo es obligatorio.'); return; }
    if (value.password !== value.confirmation) { this.error.set('Las contraseñas no coinciden.'); return; }
    this.submitting.set(true);
    this.auth.register({ email: value.email, password: value.password, nombreCompleto: value.nombreCompleto.trim() }).subscribe({
      next: () => this.router.navigate(['/login'], { queryParams: { registered: 1 }, replaceUrl: true }),
      error: err => {
        this.submitting.set(false);
        if (err?.status === 409) this.error.set('Ya existe un usuario registrado con este correo.');
        else if (err?.status === 0) this.error.set('No se pudo conectar con el servidor. Intenta nuevamente.');
        else if (err?.status === 429) this.error.set('Se realizaron demasiados intentos. Espera un momento antes de reintentar.');
        else if (err?.status >= 500) this.error.set('ArqNova no está disponible temporalmente. Intenta nuevamente más tarde.');
        else this.error.set(err?.error?.mensaje || 'No se pudo crear la cuenta. Revisa los datos ingresados.');
      }
    });
  }

  fieldError(name: 'nombreCompleto'|'email'|'password'|'confirmation') {
    const control = this.form.controls[name];
    if (!control.touched || !control.errors) return '';
    if (control.errors['required']) return ({nombreCompleto:'Nombre obligatorio.',email:'Correo electrónico obligatorio.',password:'Contraseña obligatoria.',confirmation:'Debes confirmar la contraseña.'})[name];
    if (control.errors['email']) return 'Ingresa un correo electrónico válido.';
    if (control.errors['minlength']) return name === 'password' ? 'La contraseña debe tener al menos 8 caracteres.' : 'El nombre debe tener al menos 3 caracteres.';
    return '';
  }
}
