import { UmlDiagramModel, UmlProjectStatus } from './uml-class-diagram.models';
export interface ApiResponse<T> {
  datos: T;
  mensaje?: string;
}
export interface ProjectDto {
  id: string;
  name: string;
  description: string;
  status: UmlProjectStatus;
  owner: string;
  memberRole: 'OWNER' | 'EDITOR' | 'VIEWER';
  createdAt: string;
  updatedAt: string;
}
export interface DiagramDto {
  projectId: string;
  modelVersion: number;
  model: UmlDiagramModel;
}
