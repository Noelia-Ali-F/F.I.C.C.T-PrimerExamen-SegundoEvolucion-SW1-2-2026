import { Injectable,inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BASE_PATH } from '../../api/variables';
import { FlutterGenerationUseCases } from '../../application/workspace.use-cases';
import { HttpFlutterGenerationAdapter } from '../../infrastructure/adapters/http-flutter-generation.adapter';
export type * from '../../domain/uml/generation.models';
@Injectable({providedIn:'root'})
export class UmlFlutterGeneratorService extends FlutterGenerationUseCases { constructor(){super(new HttpFlutterGenerationAdapter(inject(HttpClient),inject(BASE_PATH)));} }
