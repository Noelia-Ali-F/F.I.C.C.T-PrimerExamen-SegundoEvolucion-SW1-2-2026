import { Observable } from 'rxjs';
export interface IdentityReply {
  exito?: boolean;
  mensaje?: string;
  datos?: {
    username?: string;
    nombreCompleto?: string;
    rol?: string;
    departamento?: string;
    token?: string;
    avatarUrl?: string;
  };
}
export interface IdentityPort {
  login(value: { username: string; password: string }): Observable<IdentityReply>;
  register(value: {
    username: string;
    password: string;
    nombreCompleto: string;
    rol: 'SOLICITANTE' | 'REVISOR' | 'ADMINISTRADOR';
    departamento: string;
  }): Observable<IdentityReply>;
  currentSession(token: string): Observable<any>;
  logout(token: string): Observable<void>;
}
