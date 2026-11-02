import { CommonModule } from '@angular/common';
import { Component,Input,OnInit,OnDestroy,inject,signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { AiConversationService } from './ai-conversation.service';
import { AiConversationMode,AiTurn } from '../../domain/uml/ai-conversation.models';
import { mergeAiTurns } from '../../application/ai-conversation.use-cases';

@Component({selector:'app-project-ai-chat',standalone:true,imports:[CommonModule,FormsModule],
  template:`
    <div class="chat-info">Conversación compartida del proyecto. Consultar y revisar no modifican el lienzo.</div>
    @if(error()){<p class="chat-error" role="alert">{{error()}}</p>}
    @if(loading()){<p aria-live="polite">Cargando conversación…</p>}
    @if(enabled()){
      <div class="chat-scroll" aria-live="polite">
        @if(cursor()){<button type="button" (click)="loadOlder()" [disabled]="loadingOlder()">{{loadingOlder()?'Cargando…':'Ver mensajes anteriores'}}</button>}
        @if(!turns().length&&!loading()){<p>Comparte una consulta con tu equipo o solicita una revisión del diagrama guardado.</p>}
        @for(turn of turns();track turn.requestId){
          <article>
            <header><strong>{{turn.authorName}}</strong><time>{{turn.createdAt|date:'short'}}</time></header>
            <span class="chat-mode">{{turn.mode==='REVIEW'?'Revisión UML':'Consulta'}}</span>
            <p class="question">{{turn.instruction}}</p>
            @if(turn.state==='PENDING'){<p class="pending">La IA está preparando la respuesta…</p>}
            @else if(turn.state==='FAILED'){<p class="chat-error">{{failureText(turn.errorCode)}}</p>}
            @else if(turn.answer;as answer){
              <div class="answer"><strong>Asistente IA</strong><p>{{answer.text}}</p>
                @for(finding of answer.findings;track $index){<div class="finding" [class.warning]="finding.severity!=='INFO'"><strong>{{finding.severity==='ERROR'?'Error':finding.severity==='WARNING'?'Observación':'Información'}}</strong><p>{{finding.message}}</p></div>}
                <small>Diagrama guardado · versión {{turn.diagramVersion}}</small>
              </div>
            }
          </article>
        }
      </div>
      <form (ngSubmit)="send()">
        <label>Acción<select [(ngModel)]="mode" name="mode"><option value="ASK">Consultar a la IA</option><option value="REVIEW">Revisar diagrama</option></select></label>
        <textarea name="instruction" [(ngModel)]="instruction" maxlength="4000" rows="3" placeholder="Escribe tu consulta para el equipo" aria-label="Mensaje para la conversación compartida"></textarea>
        <button type="submit" [disabled]="sending()||!instruction.trim()">{{sending()?'Registrando…':'Enviar al equipo'}}</button>
      </form>
    }
  `,
  styles:[`:host{display:flex;flex:1;flex-direction:column;min-height:0;padding:14px;gap:12px;font-size:13px;color:#172554}.chat-info{padding:10px;background:#eff6ff;border-radius:8px;line-height:1.5}.chat-scroll{overflow-y:auto;flex:1;min-height:100px}article{padding:14px 0;border-bottom:1px solid #e2e8f0}article header{display:flex;gap:8px;justify-content:space-between}time,small,.chat-mode{font-size:11px;color:#64748b}.question{font-weight:600}.question,.answer p{white-space:pre-wrap;overflow-wrap:anywhere}.answer{padding:10px;background:#f8fafc;border-radius:8px}.finding{border-left:3px solid #2563eb;padding:6px 10px;margin-top:8px}.finding.warning{border-color:#b45309}.finding p{margin:4px 0}.chat-error{color:#b91c1c;background:#fef2f2;padding:9px;border-radius:6px}.pending{color:#475569}form{display:flex;flex-direction:column;gap:9px;border-top:1px solid #e2e8f0;padding-top:12px}label{display:flex;justify-content:space-between;gap:8px}textarea,select{font:inherit;border:1px solid #cbd5e1;border-radius:7px;padding:9px}button{font:inherit;padding:9px;border:1px solid #bfdbfe;border-radius:7px;color:#1e3a8a;background:#eff6ff;cursor:pointer}button:disabled{opacity:.6;cursor:default}`]
})
export class ProjectAiChatComponent implements OnInit,OnDestroy {
  @Input({required:true})projectId='';
  private readonly service=inject(AiConversationService);
  private subscriptions=new Subscription();private poll?:ReturnType<typeof setInterval>;private refreshing=false;private refreshAgain=false;private historyLoaded=false;
  private loadingTurns=new Set<string>();
  enabled=signal(false);loading=signal(true);sending=signal(false);error=signal('');turns=signal<AiTurn[]>([]);cursor=signal<string|null>(null);loadingOlder=signal(false);
  mode:AiConversationMode='ASK';instruction='';private requestId:string|null=null;private requestContent='';
  ngOnInit(){this.subscriptions.add(this.service.status(this.projectId).subscribe({next:status=>{
    this.enabled.set(status.enabled);this.loading.set(false);
    if(!status.enabled){this.error.set('La conversación compartida todavía no está habilitada. Puedes seguir usando Editar diagrama.');return;}
    this.refresh();this.subscriptions.add(this.service.watch(this.projectId).subscribe(change=>change.requestId?this.loadTurn(change.requestId):this.refresh()));
    this.poll=setInterval(()=>this.turns().filter(t=>t.state==='PENDING').slice(0,4).forEach(turn=>this.loadTurn(turn.requestId)),4000);
  },error:()=>{this.loading.set(false);this.error.set('No se pudo conectar con la conversación compartida.');}}));}
  ngOnDestroy(){this.subscriptions.unsubscribe();if(this.poll)clearInterval(this.poll);}
  private loadTurn(id:string){if(this.loadingTurns.has(id))return;this.loadingTurns.add(id);this.subscriptions.add(this.service.get(this.projectId,id).subscribe({next:turn=>{
    this.turns.update(turns=>mergeAiTurns(turns,[turn]));this.loadingTurns.delete(id);
  },error:()=>this.loadingTurns.delete(id)}));}
  refresh(){if(this.refreshing){this.refreshAgain=true;return;}this.refreshing=true;this.subscriptions.add(this.service.history(this.projectId).subscribe({next:page=>{
    this.turns.update(turns=>mergeAiTurns(turns,page.turns));if(!this.historyLoaded){this.cursor.set(page.nextCursor);this.historyLoaded=true;}this.refreshing=false;this.error.set('');
    if(this.refreshAgain){this.refreshAgain=false;this.refresh();}
  },error:()=>{this.refreshing=false;this.refreshAgain=false;this.error.set('No se pudo recuperar el historial. Se conservaron los mensajes visibles.');}}));}
  loadOlder(){const cursor=this.cursor();if(!cursor||this.loadingOlder())return;this.loadingOlder.set(true);this.subscriptions.add(this.service.history(this.projectId,cursor).subscribe({next:page=>{
    this.turns.update(turns=>mergeAiTurns(turns,page.turns));this.cursor.set(page.nextCursor);this.loadingOlder.set(false);
  },error:()=>{this.loadingOlder.set(false);this.error.set('No se pudieron cargar mensajes anteriores.');}}));}
  send(){if(this.sending()||!this.instruction.trim())return;this.sending.set(true);this.error.set('');
    const content=JSON.stringify([this.mode,this.instruction.trim()]);
    if(!this.requestId||this.requestContent!==content){this.requestId=crypto.randomUUID();this.requestContent=content;}const id=this.requestId;
    this.subscriptions.add(this.service.send(this.projectId,id,this.mode,this.instruction).subscribe({next:turn=>{
      this.turns.update(turns=>mergeAiTurns(turns,[turn]));this.instruction='';this.requestId=null;this.sending.set(false);this.refresh();
    },error:()=>{this.sending.set(false);this.error.set('No se confirmó el envío. Vuelve a enviar el mismo mensaje para comprobarlo sin duplicarlo.');}}));
  }
  failureText(code:string|null){switch(code){case'AI_CONTEXT_TOO_LARGE':return'El diagrama supera el contexto permitido para esta consulta. El modelo guardado se conserva.';case'AI_RATE_LIMITED':return'La IA alcanzó temporalmente su cuota. Intenta una nueva consulta más tarde.';case'AI_REQUEST_INTERRUPTED':return'La solicitud se interrumpió. Puedes enviar una nueva consulta.';case'AI_QUEUE_BUSY':return'Hay varias solicitudes en espera. Intenta más tarde.';case'ACCESS_CHANGED':return'El acceso al proyecto cambió durante la solicitud.';default:return'El proveedor de IA no pudo completar esta respuesta. El diagrama no fue modificado.';}}
}
