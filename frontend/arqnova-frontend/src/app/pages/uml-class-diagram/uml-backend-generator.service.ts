import { Injectable,inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BASE_PATH } from '../../api/variables';
import { BackendGenerationUseCases } from '../../application/workspace.use-cases';
import { HttpBackendGenerationAdapter } from '../../infrastructure/adapters/http-backend-generation.adapter';
export type * from '../../domain/uml/generation.models';
@Injectable({providedIn:'root'})
export class UmlBackendGeneratorService extends BackendGenerationUseCases { constructor(){super(new HttpBackendGenerationAdapter(inject(HttpClient),inject(BASE_PATH)));} }
