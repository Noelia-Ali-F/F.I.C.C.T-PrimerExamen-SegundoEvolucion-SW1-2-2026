import { Injectable } from '@angular/core';
import { XmiUseCases } from '../../application/workspace.use-cases';
import { EaXmiAdapter } from '../../infrastructure/adapters/ea-xmi.adapter';
export type { XmiPreview } from '../../domain/uml/xmi.models';
@Injectable({providedIn:'root'})export class UmlXmiService extends XmiUseCases {constructor(){super(new EaXmiAdapter());}}
