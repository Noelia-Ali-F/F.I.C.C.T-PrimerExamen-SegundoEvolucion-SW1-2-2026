import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AutenticacinService } from '../../api/api/autenticacin.service';
import { IdentityPort,IdentityReply } from '../../application/ports/identity.port';
export class HttpIdentityAdapter implements IdentityPort {
 constructor(private readonly api:AutenticacinService,private readonly http:HttpClient,private readonly basePath:string){}
 login(value:{username:string;password:string}){return this.api.login(value) as Observable<IdentityReply>;}
 register(value:{username:string;password:string;nombreCompleto:string;rol:'SOLICITANTE'|'REVISOR'|'ADMINISTRADOR';departamento:string}){return this.api.register(value) as Observable<IdentityReply>;}
 currentSession(token:string){return this.http.get<any>(`${this.basePath}/api/v1/auth/me`,{headers:{Authorization:`Bearer ${token}`}});}
 logout(token:string){return this.http.post<void>(`${this.basePath}/api/v1/auth/logout`,{},{headers:{Authorization:`Bearer ${token}`}});}
}
