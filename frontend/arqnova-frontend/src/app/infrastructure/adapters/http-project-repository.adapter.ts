import { HttpClient, HttpHeaders } from '@angular/common/http';
import { ProjectRepositoryPort } from '../../application/ports/project-repository.port';
import { ApiResponse, ProjectDto, DiagramDto } from '../../domain/uml/project-records';
import { UmlDiagramModel, UmlProjectStatus } from '../../domain/uml/uml-class-diagram.models';
export class HttpProjectRepositoryAdapter implements ProjectRepositoryPort {
 private readonly endpoint:string;
 constructor(private readonly http:HttpClient,basePath:string){this.endpoint=`${basePath}/api/v1/projects`;}
 list(){return this.http.get<ApiResponse<ProjectDto[]>>(this.endpoint);}
 project(id:string){return this.http.get<ApiResponse<ProjectDto>>(`${this.endpoint}/${encodeURIComponent(id)}`);}
 diagram(id:string){return this.http.get<ApiResponse<DiagramDto>>(`${this.endpoint}/${encodeURIComponent(id)}/diagram`);}
 create(value:{id:string;name:string;description:string;status:UmlProjectStatus}){return this.http.post<ApiResponse<ProjectDto>>(this.endpoint,value);}
 update(id:string,value:{name:string;description:string;status:UmlProjectStatus}){return this.http.patch<ApiResponse<ProjectDto>>(`${this.endpoint}/${encodeURIComponent(id)}`,value);}
 delete(id:string){return this.http.delete<void>(`${this.endpoint}/${encodeURIComponent(id)}`);}
 saveDiagram(id:string,model:UmlDiagramModel,version:number){const headers=new HttpHeaders({'X-Model-Version':String(version)});return this.http.put<ApiResponse<DiagramDto>>(`${this.endpoint}/${encodeURIComponent(id)}/diagram`,model,{headers});}
}
