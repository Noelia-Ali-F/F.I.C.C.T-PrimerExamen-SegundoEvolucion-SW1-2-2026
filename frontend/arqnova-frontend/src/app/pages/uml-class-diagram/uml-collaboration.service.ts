import { HttpClient } from '@angular/common/http';
import { Injectable, NgZone, inject } from '@angular/core';
import { Client } from '@stomp/stompjs';
import { Observable, map } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { BASE_PATH } from '../../api/variables';
import { UmlDiagramModel } from './uml-class-diagram.models';

export interface UmlSession {
  sessionId: string;
  projectId: string;
  projectName: string;
  inviteCode: string;
  hostUserId?: string;
  memberRole?: 'OWNER' | 'EDITOR' | 'VIEWER';
  model: UmlDiagramModel;
}

export interface UmlParticipant { userId: string; userName: string; status: string; host?: boolean; }
export type UmlLockElementType = 'class' | 'attribute' | 'relation';
export interface UmlElementLock {
  projectId: string; sessionId: string; elementId: string; elementType: UmlLockElementType;
  userId: string; userName: string; lockedAt: number; expiresAt: number;
}
export interface UmlLiveEvent {
  eventId?: string; type: string; projectId: string; sessionId?: string;
  userId?: string; userName?: string; elementId?: string; elementType?: UmlLockElementType; operation?: string;
  data?: { element?: unknown; classId?: string; attributeId?: string; attribute?: unknown; participants?: UmlParticipant[]; lock?: UmlElementLock; requesterId?: string }; timestamp?: number;
}

@Injectable({ providedIn: 'root' })
export class UmlCollaborationService {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private basePath = inject(BASE_PATH);
  private zone = inject(NgZone);
  private client?: Client;
  private session?: UmlSession;
  private handler?: (event: UmlLiveEvent) => void;

  start(projectId: string, projectName: string, model: UmlDiagramModel): Observable<UmlSession> {
    return this.http.post<any>(`${this.basePath}/api/v1/uml-collaboration/sessions`, { projectId, projectName, model })
      .pipe(map(response => response.datos as UmlSession));
  }

  join(inviteCode: string): Observable<UmlSession> {
    return this.http.get<any>(`${this.basePath}/api/v1/uml-collaboration/invitations/${encodeURIComponent(inviteCode)}`)
      .pipe(map(response => response.datos as UmlSession));
  }

  activities(sessionId: string): Observable<UmlLiveEvent[]> {
    return this.http.get<any>(`${this.basePath}/api/v1/uml-collaboration/sessions/${encodeURIComponent(sessionId)}/activities`)
      .pipe(map(response => response.datos as UmlLiveEvent[]));
  }

  connect(session: UmlSession, handler: (event: UmlLiveEvent) => void) {
    this.disconnect(false);
    this.session = session;
    this.handler = handler;
    const wsProto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const brokerURL = this.basePath.startsWith('http')
      ? this.basePath.replace(/^http/, 'ws') + '/ws-collab'
      : `${wsProto}//${window.location.host}${this.basePath}/ws-collab`;
    this.client = new Client({
      brokerURL,
      connectHeaders: { Authorization: `Bearer ${this.auth.currentUser()?.token || ''}` },
      reconnectDelay: 3000,
      heartbeatIncoming: 5000,
      heartbeatOutgoing: 5000
    });
    this.client.onConnect = () => {
      this.client!.subscribe(`/topic/uml/${session.sessionId}`, message => {
        const event = JSON.parse(message.body) as UmlLiveEvent;
        this.zone.run(() => this.handler?.(event));
      });
      this.publish({ type: 'session:join', projectId: session.projectId, operation: 'se unió a la sesión' });
    };
    this.client.onStompError = frame => this.zone.run(() => handler({
      type: 'session:error', projectId: session.projectId, operation: frame.headers['message'] || 'Error de colaboración'
    }));
    this.client.activate();
  }

  publish(event: UmlLiveEvent) {
    if (!this.client?.connected || !this.session) return;
    this.client.publish({ destination: `/app/uml/${this.session.sessionId}`, body: JSON.stringify({ ...event, sessionId: this.session.sessionId }) });
  }

  disconnect(announce = true) {
    if (announce && this.client?.connected && this.session) {
      this.publish({ type: 'session:leave', projectId: this.session.projectId, operation: 'salió de la sesión' });
    }
    this.client?.deactivate();
    this.client = undefined;
    this.session = undefined;
    this.handler = undefined;
  }
}
