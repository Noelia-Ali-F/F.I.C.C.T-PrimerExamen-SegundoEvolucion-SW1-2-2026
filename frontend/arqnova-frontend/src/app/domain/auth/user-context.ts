export interface UserContext {
  username: string;
  nombreCompleto?: string;
  rol: 'SOLICITANTE' | 'REVISOR' | 'ADMINISTRADOR';
  departamento: string;
  token?: string;
  avatarUrl?: string;
}
