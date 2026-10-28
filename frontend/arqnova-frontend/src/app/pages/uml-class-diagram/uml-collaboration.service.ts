import { HttpClient } from '@angular/common/http';
import { Injectable,NgZone,inject } from '@angular/core';
import { AuthService } from '../../auth/auth.service';
import { BASE_PATH } from '../../api/variables';
import { CollaborationUseCases } from '../../application/workspace.use-cases';
import { StompCollaborationAdapter } from '../../infrastructure/adapters/stomp-collaboration.adapter';
export type * from '../../domain/uml/collaboration.models';
@Injectable({providedIn:'root'})export class UmlCollaborationService extends CollaborationUseCases {
 constructor(){super(new StompCollaborationAdapter(inject(HttpClient),inject(AuthService),inject(BASE_PATH),inject(NgZone)));}
}
