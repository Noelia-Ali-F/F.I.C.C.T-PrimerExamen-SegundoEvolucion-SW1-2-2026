import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { BASE_PATH } from '../../api/variables';
import { UmlDiagramModel } from './uml-class-diagram.models';

export interface BackendGenerationConfig {
  projectName: string;
  basePackage: string;
  includeBasicConfig: boolean;
  model: UmlDiagramModel;
}

@Injectable({ providedIn: 'root' })
export class UmlBackendGeneratorService {
  private readonly http = inject(HttpClient);
  private readonly basePath = inject(BASE_PATH);

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
