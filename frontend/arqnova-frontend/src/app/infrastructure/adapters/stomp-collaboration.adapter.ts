import { CollaborationPort,SessionReaderPort } from '../../application/ports/workspace.ports';
import { UmlSession,UmlLiveEvent } from '../../domain/uml/collaboration.models';
import { HttpClient } from '@angular/common/http';
import { NgZone } from '@angular/core';
import { Client } from '@stomp/stompjs';
import { Observable, map } from 'rxjs';

import { UmlDiagramModel } from '../../domain/uml/uml-class-diagram.models';

export class StompCollaborationAdapter implements CollaborationPort {
  constructor(private readonly http:HttpClient,private readonly auth:SessionReaderPort,private readonly basePath:string,private readonly zone:NgZone){}
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
