import { UmlAiCommand,UmlImageAnalysis } from '../../domain/uml/ai.models';
import { UmlAiPort,FileUpload } from '../../application/ports/workspace.ports';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { UmlDiagramModel } from '../../domain/uml/uml-class-diagram.models';

export class HttpUmlAiAdapter implements UmlAiPort {
  constructor(private readonly http:HttpClient,private readonly basePath:string){}

  interpret(instruction: string, model: UmlDiagramModel): Observable<UmlAiCommand> {
    return this.http.post<any>(`${this.basePath}/api/v1/uml-ai/interpret`, { instruction, model })
      .pipe(map(response => response.datos as UmlAiCommand));
  }

  analyzeImage(image: FileUpload): Observable<UmlImageAnalysis> {
    const form = new FormData(); form.append('image', image as File);
    return this.http.post<any>(`${this.basePath}/api/v1/uml-ai/image`, form)
      .pipe(map(response => response.datos as UmlImageAnalysis));
  }
}
