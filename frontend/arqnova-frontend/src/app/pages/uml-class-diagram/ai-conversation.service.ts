import { Injectable,inject,NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BASE_PATH } from '../../api/variables';
import { AuthService } from '../../auth/auth.service';
import { AiConversationUseCases } from '../../application/ai-conversation.use-cases';
import { HttpAiConversationAdapter } from '../../infrastructure/adapters/http-ai-conversation.adapter';
@Injectable({providedIn:'root'})
export class AiConversationService extends AiConversationUseCases {
  constructor(){const auth=inject(AuthService);super(new HttpAiConversationAdapter(inject(HttpClient),inject(BASE_PATH),()=>auth.currentUser()?.token||'',inject(NgZone)));}
}
