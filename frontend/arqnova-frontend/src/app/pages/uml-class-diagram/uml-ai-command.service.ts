import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { BASE_PATH } from '../../api/variables';
import { UmlDiagramModel } from './uml-class-diagram.models';

export type UmlAiStatus = 'READY' | 'CLARIFICATION' | 'ERROR';
export type UmlAiOperation = 'CLASS_CREATE' | 'CLASS_UPDATE' | 'CLASS_DELETE'
  | 'ATTRIBUTE_CREATE' | 'ATTRIBUTE_UPDATE' | 'ATTRIBUTE_DELETE'
  | 'METHOD_CREATE' | 'METHOD_UPDATE' | 'METHOD_DELETE'
  | 'RELATION_CREATE' | 'RELATION_UPDATE' | 'RELATION_DELETE';

export interface UmlAiCommand {
  status: UmlAiStatus;
  operation?: UmlAiOperation;
  targetId?: string;
  parameters: Record<string, any>;
  message: string;
  operations?: UmlAiOperationItem[];
  clarificationQuestion?: string;
  interpreter?: 'OPENAI' | 'DETERMINISTIC_FALLBACK';
  fallbackUsed?: boolean;
}

export interface UmlAiOperationItem {
  type: UmlAiOperation;
  targetId?: string;
  elementType: 'class' | 'attribute' | 'relation';
  target: Record<string, string>;
  parameters: Record<string, any>;
}

export interface UmlImageAttribute { id?: string; name: string; type: string; isPrimaryKey: boolean; }
export interface UmlImageClass { name: string; attributes: UmlImageAttribute[]; methods?: { name: string; parameters: string; returnType: string; }[]; x?: number; y?: number; }
export interface UmlImageRelation { sourceClassName: string; targetClassName: string; type: string; label: string; sourceMultiplicity: string; targetMultiplicity: string; associationClassName?: string; }
export interface UmlImageAnalysis { status: 'READY'|'ERROR'; classes: UmlImageClass[]; relations: UmlImageRelation[]; warnings: string[]; message: string; provider: 'OPENAI'; model: string; }

@Injectable({ providedIn: 'root' })
export class UmlAiCommandService {
  private readonly http = inject(HttpClient);
  private readonly basePath = inject(BASE_PATH);

  interpret(instruction: string, model: UmlDiagramModel): Observable<UmlAiCommand> {
    return this.http.post<any>(`${this.basePath}/api/v1/uml-ai/interpret`, { instruction, model })
      .pipe(map(response => response.datos as UmlAiCommand));
  }

  analyzeImage(image: File): Observable<UmlImageAnalysis> {
    const form = new FormData(); form.append('image', image);
    return this.http.post<any>(`${this.basePath}/api/v1/uml-ai/image`, form)
      .pipe(map(response => response.datos as UmlImageAnalysis));
  }
}
