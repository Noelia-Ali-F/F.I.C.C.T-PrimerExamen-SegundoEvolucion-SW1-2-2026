import { UmlDiagramModel } from './uml-class-diagram.models';
export interface XmiPreview {
  model: UmlDiagramModel;
  warnings: string[];
}
