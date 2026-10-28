export type UmlAiStatus = 'READY' | 'CLARIFICATION' | 'ERROR';
export type UmlAiOperation =
  | 'CLASS_CREATE'
  | 'CLASS_UPDATE'
  | 'CLASS_DELETE'
  | 'ATTRIBUTE_CREATE'
  | 'ATTRIBUTE_UPDATE'
  | 'ATTRIBUTE_DELETE'
  | 'METHOD_CREATE'
  | 'METHOD_UPDATE'
  | 'METHOD_DELETE'
  | 'RELATION_CREATE'
  | 'RELATION_UPDATE'
  | 'RELATION_DELETE';

export interface UmlAiCommand {
  status: UmlAiStatus;
  operation?: UmlAiOperation;
  targetId?: string;
  parameters: Record<string, any>;
  message: string;
  operations?: UmlAiOperationItem[];
  clarificationQuestion?: string;
  interpreter?: 'OPENAI' | 'GEMINI' | 'GROQ' | 'DETERMINISTIC_FALLBACK';
  fallbackUsed?: boolean;
}

export interface UmlAiOperationItem {
  type: UmlAiOperation;
  targetId?: string;
  elementType: 'class' | 'attribute' | 'relation';
  target: Record<string, string>;
  parameters: Record<string, any>;
}

export interface UmlImageAttribute {
  id?: string;
  name: string;
  type: string;
  isPrimaryKey: boolean;
}
export interface UmlImageClass {
  name: string;
  attributes: UmlImageAttribute[];
  methods?: { name: string; parameters: string; returnType: string }[];
  x?: number;
  y?: number;
}
export interface UmlImageRelation {
  sourceClassName: string;
  targetClassName: string;
  type: string;
  label: string;
  sourceMultiplicity: string;
  targetMultiplicity: string;
  associationClassName?: string;
}
export interface UmlImageAnalysis {
  status: 'READY' | 'ERROR';
  classes: UmlImageClass[];
  relations: UmlImageRelation[];
  warnings: string[];
  message: string;
  provider: 'OPENAI' | 'GEMINI' | 'GROQ';
  model: string;
}
