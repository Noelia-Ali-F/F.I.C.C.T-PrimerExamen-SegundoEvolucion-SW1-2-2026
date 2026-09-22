import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { AutenticacinService } from '../api/api/autenticacin.service';
import { catchError, finalize, map, Observable, of, shareReplay, tap } from 'rxjs';
import { BASE_PATH } from '../api/variables';

export interface UserContext {
  username: string;
  nombreCompleto?: string;
  rol: 'SOLICITANTE' | 'REVISOR' | 'ADMINISTRADOR';
  departamento: string;
  token?: string;
  avatarUrl?: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private static readonly STORAGE_KEY = 'ARQNOVA_USER';
  private static readonly LEGACY_STORAGE_KEY = 'WORKFLOW_SYS_USER';
  private authApi = inject(AutenticacinService);
  private http = inject(HttpClient);
  private basePath = inject(BASE_PATH);

  currentUser = signal<UserContext | null>(null);
  sessionReady = signal(false);
  private restoreRequest?: Observable<boolean>;

  constructor() {
    if (!localStorage.getItem(AuthService.STORAGE_KEY) && !localStorage.getItem(AuthService.LEGACY_STORAGE_KEY)) {
      this.sessionReady.set(true);
    }
  }

  login(credentials: { username: string, password: string }): Observable<UserContext> {
    const username = (credentials.username || '').trim();
    const password = credentials.password || '';

    if (!username || !password) {
      throw new Error('Usuario y contraseña son obligatorios');
    }

    return this.authApi.login({ username, password }).pipe(
      map((response) => {
        if (!response.exito || !response.datos) {
          throw new Error(response.mensaje || 'Respuesta de login inválida');
        }

        const payload = response.datos;
        const ctx: UserContext = {
          username: payload.username || username,
          nombreCompleto: payload.nombreCompleto || '',
          rol: (payload.rol as UserContext['rol']) || 'SOLICITANTE',
          departamento: payload.departamento || 'Sin Departamento',
          token: payload.token || '',
          avatarUrl: (payload as any).avatarUrl || ''
        };

        return ctx;
      }),
      tap((ctx) => {
        this.currentUser.set(ctx);
        localStorage.setItem(AuthService.STORAGE_KEY, JSON.stringify(ctx));
        this.sessionReady.set(true);
      })
    );
  }

  register(data: { email: string; password: string; nombreCompleto: string }): Observable<void> {
    return this.authApi.register({
      username: data.email.trim().toLowerCase(),
      password: data.password,
      nombreCompleto: data.nombreCompleto.trim(),
      rol: 'SOLICITANTE',
      departamento: 'Sistemas'
    }).pipe(map(response => {
      if (!response?.exito) throw new Error(response?.mensaje || 'No se pudo crear la cuenta');
      return void 0;
    }));
  }

  ensureSession(): Observable<boolean> {
    const activeSession = this.currentUser() || this.readStoredSession();
    if (!activeSession?.token || this.isTokenExpired(activeSession.token)) {
      this.clearSession();
      return of(false);
    }
    if (this.sessionReady() && this.currentUser()) return of(true);
    if (this.restoreRequest) return this.restoreRequest;
    this.restoreRequest = this.http.get<any>(`${this.basePath}/api/v1/auth/me`, {
      headers: { Authorization: `Bearer ${activeSession.token}` }
    }).pipe(
      map(response => {
        const payload = response?.datos;
        if (!payload) return false;
        const restored: UserContext = {
          username: payload.username,
          nombreCompleto: payload.nombreCompleto || '',
          rol: payload.rol,
          departamento: payload.departamento || 'Sin Departamento',
          avatarUrl: payload.avatarUrl || '',
          token: activeSession.token
        };
        this.currentUser.set(restored);
        localStorage.setItem(AuthService.STORAGE_KEY, JSON.stringify(restored));
        return true;
      }),
      catchError(() => { this.clearSession(); return of(false); }),
      finalize(() => { this.sessionReady.set(true); this.restoreRequest = undefined; }),
      shareReplay(1)
    );
    return this.restoreRequest;
  }

  logout(): Observable<void> {
    const token = this.currentUser()?.token || this.readStoredSession()?.token;
    const request = token
      ? this.http.post<void>(`${this.basePath}/api/v1/auth/logout`, {}, { headers: { Authorization: `Bearer ${token}` } }).pipe(catchError(() => of(void 0)))
      : of(void 0);
    return request.pipe(finalize(() => this.clearSession()));
  }

  clearSession() {
    this.currentUser.set(null);
    this.sessionReady.set(true);
    localStorage.removeItem(AuthService.STORAGE_KEY);
    localStorage.removeItem(AuthService.LEGACY_STORAGE_KEY);
  }

  /** Call this after admin updates their own avatar to keep the session in sync */
  updateCurrentUser(patch: Partial<Pick<UserContext, 'avatarUrl' | 'nombreCompleto'>>) {
    const current = this.currentUser();
    if (!current) return;
    const updated: UserContext = { ...current, ...patch };
    this.currentUser.set(updated);
    localStorage.setItem(AuthService.STORAGE_KEY, JSON.stringify(updated));
  }

  hasAnyRole(roles: Array<UserContext['rol']>): boolean {
    const current = this.currentUser();
    return !!current && roles.includes(current.rol);
  }

  isSolicitante(): boolean {
    return this.currentUser()?.rol === 'SOLICITANTE';
  }

  isRevisor(): boolean {
    return this.currentUser()?.rol === 'REVISOR';
  }

  isAdministrador(): boolean {
    return this.currentUser()?.rol === 'ADMINISTRADOR';
  }

  getValidAvatar(url: string | undefined | null): string {
    if (!url || typeof url !== 'string') return '/icons/default-avatar.png';
    const trimmed = url.trim();
    if (!trimmed || trimmed === 'null' || trimmed === 'undefined' || trimmed.toLowerCase().includes('randomuser') || trimmed.toLowerCase().includes('ui-avatars') || trimmed.toLowerCase().includes('dicebear')) {
      return '/icons/default-avatar.png';
    }
    return trimmed;
  }

  private readStoredSession(): UserContext | null {
    const current = localStorage.getItem(AuthService.STORAGE_KEY);
    const legacy = localStorage.getItem(AuthService.LEGACY_STORAGE_KEY);
    try {
      const session = JSON.parse(current || legacy || 'null') as UserContext | null;
      if (!current && session) {
        localStorage.setItem(AuthService.STORAGE_KEY, JSON.stringify(session));
        localStorage.removeItem(AuthService.LEGACY_STORAGE_KEY);
      }
      return session;
    } catch {
      localStorage.removeItem(AuthService.STORAGE_KEY);
      localStorage.removeItem(AuthService.LEGACY_STORAGE_KEY);
      return null;
    }
  }

  private isTokenExpired(token: string): boolean {
    try {
      const parts = token.split('.');
      return parts.length !== 5 || Number(parts[3]) * 1000 <= Date.now();
    } catch { return true; }
  }
}
