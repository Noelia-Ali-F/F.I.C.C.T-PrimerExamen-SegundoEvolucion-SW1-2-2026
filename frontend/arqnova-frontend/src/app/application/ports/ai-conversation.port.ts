import { Observable } from 'rxjs';
import { AiConversationMode,AiConversationPage,AiTurn,AiConversationChange } from '../../domain/uml/ai-conversation.models';
export interface AiConversationPort {
  status(projectId: string): Observable<{enabled:boolean;modes?:AiConversationMode[]}>;
  history(projectId: string, cursor?: string): Observable<AiConversationPage>;
  get(projectId: string, requestId: string): Observable<AiTurn>;
  send(projectId: string, requestId: string, mode: AiConversationMode, instruction: string): Observable<AiTurn>;
  watch(projectId: string): Observable<AiConversationChange>;
}
