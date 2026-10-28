import { BackendGenerationConfig,FlutterGenerationConfig } from '../../domain/uml/generation.models';
import { FlutterGenerationPort,FileUpload } from '../../application/ports/workspace.ports';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UmlDiagramModel } from '../../domain/uml/uml-class-diagram.models';

export class HttpFlutterGenerationAdapter implements FlutterGenerationPort {
  constructor(private readonly http:HttpClient,private readonly basePath:string){}

  generate(config: FlutterGenerationConfig): Observable<HttpResponse<Blob>> {
    return this.http.post(`${this.basePath}/api/v1/uml-flutter/generate`, config, {
      observe: 'response', responseType: 'blob'
    });
  }
}
