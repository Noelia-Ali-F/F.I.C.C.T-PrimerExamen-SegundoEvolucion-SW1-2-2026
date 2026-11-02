import { Observable } from 'rxjs';
import { AiConversationMode,AiConversationPage,AiTurn,AiConversationChange } from '../../domain/uml/ai-conversation.models';
export interface AiConversationPort {
  status(projectId: string): Observable<{enabled:boolean}>;
  history(projectId: string, cursor?: string): Observable<AiConversationPage>;
  send(projectId: string, requestId: string, mode: AiConversationMode, instruction: string): Observable<AiTurn>;
  watch(projectId: string): Observable<AiConversationChange>;
}
