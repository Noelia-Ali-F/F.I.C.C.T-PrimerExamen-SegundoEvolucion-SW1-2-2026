import { Injectable,inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BASE_PATH } from '../../api/variables';
import { UmlAiUseCases } from '../../application/workspace.use-cases';
import { HttpUmlAiAdapter } from '../../infrastructure/adapters/http-uml-ai.adapter';
export type * from '../../domain/uml/ai.models';
@Injectable({providedIn:'root'})
export class UmlAiCommandService extends UmlAiUseCases { constructor(){super(new HttpUmlAiAdapter(inject(HttpClient),inject(BASE_PATH)));} }
