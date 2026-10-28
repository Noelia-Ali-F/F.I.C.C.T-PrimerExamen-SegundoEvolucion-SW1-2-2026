import { UserAdminPort,FileUpload } from '../../application/ports/workspace.ports';
import { AdminUser,CrearAdminUserRequest,ActualizarAdminUserRequest } from '../../domain/auth/admin.models';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';

interface ApiResponse<T> {
  exito?: boolean;
  mensaje?: string;
  datos?: T;
}

export class HttpAdminUsersAdapter implements UserAdminPort {
  private endpoint: string;

  constructor(
    private http: HttpClient,
    private basePath: string
  ) {
    this.endpoint = `${this.basePath}/api/v1/admin/usuarios`;
  }

  listarUsuarios(): Observable<AdminUser[]> {
    return this.http
      .get<ApiResponse<AdminUser[]>>(this.endpoint)
      .pipe(map((res) => res.datos || []));
  }

  crearUsuario(request: CrearAdminUserRequest): Observable<AdminUser> {
    return this.http
      .post<ApiResponse<AdminUser>>(this.endpoint, request)
      .pipe(map((res) => this.unwrapOne(res, 'No se pudo crear el usuario')));
  }

  actualizarUsuario(id: string, request: ActualizarAdminUserRequest): Observable<AdminUser> {
    return this.http
      .put<ApiResponse<AdminUser>>(`${this.endpoint}/${id}`, request)
      .pipe(map((res) => this.unwrapOne(res, 'No se pudo actualizar el usuario')));
  }

  eliminarUsuario(id: string): Observable<void> {
    return this.http
      .delete<ApiResponse<Record<string, string>>>(`${this.endpoint}/${id}`)
      .pipe(map(() => void 0));
  }

  subirAvatar(id: string, archivo: FileUpload): Observable<AdminUser> {
    const formData = new FormData();
    formData.append('archivo', archivo as File);

    return this.http
      .post<ApiResponse<AdminUser>>(`${this.endpoint}/${id}/avatar`, formData)
      .pipe(map((res) => this.unwrapOne(res, 'No se pudo subir el avatar')));
  }

  actualizarEstado(id: string, habilitado: boolean): Observable<AdminUser> {
    return this.http.patch<ApiResponse<AdminUser>>(`${this.endpoint}/${id}/estado`, { habilitado })
      .pipe(map((res) => this.unwrapOne(res, 'No se pudo actualizar el estado del usuario')));
  }

  listarDepartamentos(): Observable<string[]> {
    return this.http
      .get<ApiResponse<string[]>>(`${this.basePath}/api/v1/departamentos/nombres`)
      .pipe(map((res) => res.datos || []));
  }


  private unwrapOne<T>(res: ApiResponse<T>, fallbackMessage: string): T {
    if (!res.datos) {
      throw new Error(res.mensaje || fallbackMessage);
    }
    return res.datos;
  }
}
