import { BackendGenerationConfig,FlutterGenerationConfig } from '../../domain/uml/generation.models';
import { BackendGenerationPort,FileUpload } from '../../application/ports/workspace.ports';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UmlDiagramModel } from '../../domain/uml/uml-class-diagram.models';

export class HttpBackendGenerationAdapter implements BackendGenerationPort {
  constructor(private readonly http:HttpClient,private readonly basePath:string){}

  generate(config: BackendGenerationConfig): Observable<HttpResponse<Blob>> {
    return this.http.post(`${this.basePath}/api/v1/uml-backend/generate`, config, {
      observe: 'response', responseType: 'blob'
    });
  }
  generatePostman(config: BackendGenerationConfig): Observable<HttpResponse<Blob>> {
    return this.http.post(`${this.basePath}/api/v1/uml-backend/postman`, config, {
      observe: 'response', responseType: 'blob'
    });
  }
}
