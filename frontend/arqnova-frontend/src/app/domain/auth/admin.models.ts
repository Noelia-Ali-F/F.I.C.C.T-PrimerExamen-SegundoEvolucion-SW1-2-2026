export type RolUsuario = 'SOLICITANTE' | 'REVISOR' | 'ADMINISTRADOR';

export interface AdminUser {
  id: string;
  username: string;
  nombreCompleto: string;
  rol: RolUsuario;
  departamento?: string;
  fechaCreacion?: string;
  avatarUrl?: string;
  habilitado: boolean;
}

export interface CrearAdminUserRequest {
  username: string;
  password: string;
  nombreCompleto: string;
  rol: RolUsuario;
  departamento?: string;
  avatarUrl?: string;
}

export interface ActualizarAdminUserRequest {
  nombreCompleto: string;
  rol: RolUsuario;
  departamento?: string;
  password?: string;
  avatarUrl?: string;
}
