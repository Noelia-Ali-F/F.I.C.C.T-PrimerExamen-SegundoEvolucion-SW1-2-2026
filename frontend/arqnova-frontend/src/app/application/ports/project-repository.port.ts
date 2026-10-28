import { Observable } from 'rxjs';
import { ApiResponse, ProjectDto, DiagramDto } from '../../domain/uml/project-records';
import { UmlDiagramModel, UmlProjectStatus } from '../../domain/uml/uml-class-diagram.models';
export interface ProjectRepositoryPort {
  list(): Observable<ApiResponse<ProjectDto[]>>;
  project(id: string): Observable<ApiResponse<ProjectDto>>;
  diagram(id: string): Observable<ApiResponse<DiagramDto>>;
  create(value: {
    id: string;
    name: string;
    description: string;
    status: UmlProjectStatus;
  }): Observable<ApiResponse<ProjectDto>>;
  update(
    id: string,
    value: { name: string; description: string; status: UmlProjectStatus },
  ): Observable<ApiResponse<ProjectDto>>;
  delete(id: string): Observable<void>;
  saveDiagram(
    id: string,
    model: UmlDiagramModel,
    version: number,
  ): Observable<ApiResponse<DiagramDto>>;
}
