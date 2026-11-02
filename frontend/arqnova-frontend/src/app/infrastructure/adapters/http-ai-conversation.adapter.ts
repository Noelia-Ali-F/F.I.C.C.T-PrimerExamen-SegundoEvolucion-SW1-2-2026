import { HttpClient,HttpParams } from '@angular/common/http';
import { NgZone } from '@angular/core';
import { Observable,map } from 'rxjs';
import { Client } from '@stomp/stompjs';
import { AiConversationPort } from '../../application/ports/ai-conversation.port';
import { AiConversationMode,AiConversationPage,AiTurn,AiConversationChange } from '../../domain/uml/ai-conversation.models';
export class HttpAiConversationAdapter implements AiConversationPort {
  constructor(private readonly http:HttpClient,private readonly base:string,private readonly token:()=>string,private readonly zone:NgZone){}
  private path(project:string){return `${this.base}/api/v1/projects/${encodeURIComponent(project)}/ai`;}
  status(project:string){return this.http.get<any>(`${this.path(project)}/status`).pipe(map(r=>r.datos as {enabled:boolean}));}
  history(project:string,cursor?:string){return this.http.get<any>(`${this.path(project)}/messages`,{params:cursor?new HttpParams().set('cursor',cursor):undefined}).pipe(map(r=>r.datos as AiConversationPage));}
  get(project:string,requestId:string){return this.http.get<any>(`${this.path(project)}/messages/${encodeURIComponent(requestId)}`).pipe(map(r=>r.datos as AiTurn));}
  send(project:string,requestId:string,mode:AiConversationMode,instruction:string){return this.http.post<any>(`${this.path(project)}/messages`,{requestId,mode,instruction}).pipe(map(r=>r.datos as AiTurn));}
  watch(project:string):Observable<AiConversationChange>{
    return new Observable(observer=>{
      const url=this.base.startsWith('http')?this.base.replace(/^http/,'ws')+'/ws-collab':`${location.protocol==='https:'?'wss:':'ws:'}//${location.host}${this.base}/ws-collab`;
      const client=new Client({brokerURL:url,connectHeaders:{Authorization:`Bearer ${this.token()}`},reconnectDelay:3000,heartbeatIncoming:5000,heartbeatOutgoing:5000});
      client.onConnect=()=>{
        client.subscribe(`/topic/ai/${project}`,message=>this.zone.run(()=>observer.next(JSON.parse(message.body))));
        this.zone.run(()=>observer.next({projectId:project,state:'CONNECTED'}));
      };
      client.activate();return()=>{void client.deactivate();};
    });
  }
}
