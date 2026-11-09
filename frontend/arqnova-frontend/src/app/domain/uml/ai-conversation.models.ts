export type AiConversationMode = 'ASK' | 'REVIEW' | 'ADVISE' | 'MEDIATE' | 'MODEL';
export type AiTurnState = 'PENDING' | 'COMPLETED' | 'FAILED';
export interface AiFinding { severity: 'INFO' | 'WARNING' | 'ERROR'; elementId: string | null; message: string; }
export interface AiTurn {
  requestId: string; projectId: string; authorId: string; authorName: string;
  mode: AiConversationMode; instruction: string; state: AiTurnState;
  answer: { text: string; findings: AiFinding[]; provider: string; model: string } | null;
  errorCode: string | null; diagramVersion: number; createdAt: string; updatedAt: string;
}
export interface AiConversationPage { turns: AiTurn[]; nextCursor: string | null; }
export interface AiConversationChange { projectId: string; requestId?: string; state: AiTurnState | 'CONNECTED'; mode?: AiConversationMode; diagramVersion?: number; }
