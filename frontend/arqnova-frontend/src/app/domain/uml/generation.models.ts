import { UmlDiagramModel } from './uml-class-diagram.models';
export interface BackendGenerationConfig {
  projectName: string;
  basePackage: string;
  includeBasicConfig: boolean;
  model: UmlDiagramModel;
}

export interface FlutterGenerationConfig {
  projectName: string;
  backendBaseUrl: string;
  model: UmlDiagramModel;
}
