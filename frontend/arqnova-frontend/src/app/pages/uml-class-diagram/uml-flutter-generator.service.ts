import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { BASE_PATH } from '../../api/variables';
import { UmlDiagramModel } from './uml-class-diagram.models';

export interface FlutterGenerationConfig {
  projectName: string;
  backendBaseUrl: string;
  model: UmlDiagramModel;
}

@Injectable({ providedIn: 'root' })
export class UmlFlutterGeneratorService {
  private readonly http = inject(HttpClient);
  private readonly basePath = inject(BASE_PATH);

  generate(config: FlutterGenerationConfig): Observable<HttpResponse<Blob>> {
    return this.http.post(`${this.basePath}/api/v1/uml-flutter/generate`, config, {
      observe: 'response', responseType: 'blob'
    });
  }
}
