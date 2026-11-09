import { AuthService } from '../../auth/auth.service';
import { CommonModule } from '@angular/common';
import { Component,Input,OnInit,OnDestroy,inject,output,signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { AiConversationService } from './ai-conversation.service';
import { AiConversationMode,AiTurn } from '../../domain/uml/ai-conversation.models';
import { mergeAiTurns } from '../../application/ai-conversation.use-cases';

@Component({selector:'app-project-ai-chat',standalone:true,imports:[CommonModule,FormsModule],
  template:`
    <div class="chat-info">Conversación compartida del proyecto. Solo el modo Modificar diagrama puede editar el lienzo.</div>
    @if(error()){<p class="chat-error" role="alert">{{error()}}</p><button type="button" (click)="recover()">Recuperar conversación</button>}
    @if(loading()){<p aria-live="polite">Cargando conversación…</p>}
    @if(enabled()){
      <div class="chat-scroll" aria-live="polite">
        @if(cursor()){<button type="button" (click)="loadOlder()" [disabled]="loadingOlder()">{{loadingOlder()?'Cargando…':'Ver mensajes anteriores'}}</button>}
        @if(!turns().length&&!loading()){<p>Comparte una consulta con tu equipo o solicita una revisión del diagrama guardado.</p>}
        @for(turn of turns();track turn.requestId){
          <article>
            <header><strong>{{turn.authorName}}</strong><time>{{turn.createdAt|date:'short'}}</time></header>
            <span class="chat-mode">{{modeLabels[turn.mode]}}</span>
            <p class="question">{{turn.instruction}}</p>
            @if(turn.state==='PENDING'){<p class="pending">La solicitud está en proceso o en recuperación. El resultado aparecerá cuando quede guardado.</p>}
            @else if(turn.state==='FAILED'){<p class="chat-error">{{failureText(turn.errorCode)}}</p>}
            @else if(turn.answer;as answer){
              <div class="answer"><strong>Asistente IA</strong><p>{{answer.text}}</p>
                @for(finding of answer.findings;track $index){<div class="finding" [class.warning]="finding.severity!=='INFO'"><strong>{{finding.severity==='ERROR'?'Error':finding.severity==='WARNING'?'Observación':'Información'}}</strong><p>{{finding.message}}</p></div>}
                @if(turn.mode!=='MODEL'){<small>Sin cambios en el lienzo · versión {{turn.diagramVersion}}</small>}
                @else{<small>Diagrama guardado · versión {{turn.diagramVersion}}</small>}
                @if(answer.model==='modeling-mode-required' && modelingEnabled()){
                  <button type="button" [disabled]="sending() || !!instruction.trim()" (click)="prepareModeling(turn.instruction)">Preparar cambio UML</button>
                }
              </div>
            }
          </article>
        }
      </div>
      <form (ngSubmit)="send()">
        <label>Acción<select [(ngModel)]="mode" name="mode"><option value="ASK">Consultar a la IA</option><option value="REVIEW">Revisar diagrama</option>@if(availableModes().includes('ADVISE')){<option value="ADVISE">Pedir asesoramiento</option>}@if(availableModes().includes('MEDIATE')){<option value="MEDIATE">Mediar entre propuestas</option>}@if(modelingEnabled()){<option value="MODEL">Modificar diagrama guardado</option>}</select></label>
        @if(mode==='ASK'){<p>Las consultas no editan el lienzo. @if(modelingEnabled()){Para aplicar una orden, elige Modificar diagrama guardado.}</p>}
        @if(mode==='MEDIATE'){<p>Describe las propuestas o indica cuáles debe comparar la IA. El equipo toma la decisión.</p>}
        @if(mode==='MODEL'){<p>La solicitud se aplicará al diagrama guardado para todo el equipo. Guarda y termina las ediciones manuales antes de enviarla.</p>}
        <textarea name="instruction" [(ngModel)]="instruction" maxlength="4000" rows="3" placeholder="Escribe tu consulta para el equipo" aria-label="Mensaje para la conversación compartida"></textarea>
        <button type="submit" [disabled]="sending()||!instruction.trim()">{{sending()?'Registrando…':'Enviar al equipo'}}</button>
      </form>
    }
  `,
  styles:[`:host{display:flex;flex:1;flex-direction:column;min-height:0;padding:14px;gap:12px;font-size:13px;color:#172554}.chat-info{padding:10px;background:#eff6ff;border-radius:8px;line-height:1.5}.chat-scroll{overflow-y:auto;flex:1;min-height:100px}article{padding:14px 0;border-bottom:1px solid #e2e8f0}article header{display:flex;gap:8px;justify-content:space-between}time,small,.chat-mode{font-size:11px;color:#64748b}.question{font-weight:600}.question,.answer p{white-space:pre-wrap;overflow-wrap:anywhere}.answer{padding:10px;background:#f8fafc;border-radius:8px}.finding{border-left:3px solid #2563eb;padding:6px 10px;margin-top:8px}.finding.warning{border-color:#b45309}.finding p{margin:4px 0}.chat-error{color:#b91c1c;background:#fef2f2;padding:9px;border-radius:6px}.pending{color:#475569}form{display:flex;flex-direction:column;gap:9px;border-top:1px solid #e2e8f0;padding-top:12px}label{display:flex;justify-content:space-between;gap:8px}textarea,select{font:inherit;border:1px solid #cbd5e1;border-radius:7px;padding:9px}button{font:inherit;padding:9px;border:1px solid #bfdbfe;border-radius:7px;color:#1e3a8a;background:#eff6ff;cursor:pointer}button:disabled{opacity:.6;cursor:default}`]
})
export class ProjectAiChatComponent implements OnInit,OnDestroy {
  @Input({required:true})projectId='';
  readonly modelCompleted = output<number>();
  private readonly service=inject(AiConversationService);
  private readonly auth=inject(AuthService);
  private storageKey='';
  private reconcilingSends=false;
  private unconfirmed: {requestId:string;mode:AiConversationMode;instruction:string}[]=[];
  private subscriptions=new Subscription();private poll?:ReturnType<typeof setInterval>;private refreshing=false;private refreshAgain=false;private historyLoaded=false;
  private reconciliationTimer?: ReturnType<typeof setInterval>;
  private historyHead: string | null = null;
  private catchUp: { head: string | null; cursor: string } | null = null;
  private loadingTurns=new Set<string>();
  readonly availableModes = signal<AiConversationMode[]>([]);
  readonly modeLabels: Record<AiConversationMode, string> = {
    ASK: 'Consulta', REVIEW: 'Revisión UML', ADVISE: 'Asesoramiento', MEDIATE: 'Mediación', MODEL: 'Modelado UML'
  };
  modelingEnabled=signal(false);enabled=signal(false);loading=signal(true);sending=signal(false);error=signal('');turns=signal<AiTurn[]>([]);cursor=signal<string|null>(null);loadingOlder=signal(false);
  mode:AiConversationMode='ASK';instruction='';private requestId:string|null=null;private requestContent='';
  ngOnInit(){this.subscriptions.add(this.service.status(this.projectId).subscribe({next:status=>{
    this.availableModes.set(status.modes ?? []);
    this.modelingEnabled.set(this.availableModes().includes('MODEL'));this.enabled.set(status.enabled);this.loading.set(false);
    if(!status.enabled){this.error.set('La conversación compartida todavía no está habilitada. Puedes seguir usando Editar diagrama.');return;}
    this.restoreUnconfirmed();
    this.refresh();this.subscriptions.add(this.service.watch(this.projectId).subscribe(change=>change.requestId?this.loadTurn(change.requestId):this.refresh()));
    this.reconciliationTimer = setInterval(() => {
      if (!this.refreshing && !this.loadingOlder()) this.refresh();
    }, 15_000);
    this.poll=setInterval(()=>this.turns().filter(t=>t.state==='PENDING').slice(0,4).forEach(turn=>this.loadTurn(turn.requestId)),4000);
  },error:()=>{this.loading.set(false);this.error.set('No se pudo conectar con la conversación compartida.');}}));}
  ngOnDestroy(){this.subscriptions.unsubscribe();if(this.poll)clearInterval(this.poll);clearInterval(this.reconciliationTimer);}
  private acceptTurns(incoming: AiTurn[]) {
    for (const turn of incoming) {
      if (turn.projectId === this.projectId && turn.authorId === this.auth.currentUser()?.username) {
        this.unconfirmed = this.unconfirmed.filter(pending => !(pending.requestId === turn.requestId
          && pending.mode === turn.mode && pending.instruction === turn.instruction));
      }
    }
    this.persistUnconfirmed();
    this.turns.update(turns => mergeAiTurns(turns, incoming));
    const version = incoming.reduce((latest, turn) =>
      turn.mode === 'MODEL' && turn.state === 'COMPLETED' ? Math.max(latest, turn.diagramVersion) : latest, 0);
    if (version > 0) this.modelCompleted.emit(version);
  }
  private loadTurn(id:string){if(this.loadingTurns.has(id))return;this.loadingTurns.add(id);this.subscriptions.add(this.service.get(this.projectId,id).subscribe({next:turn=>{
    this.acceptTurns([turn]);this.loadingTurns.delete(id);
  },error:()=>{
    this.loadingTurns.delete(id);
    this.error.set('No se pudo comprobar el estado de una solicitud. El último estado visible puede estar desactualizado.');
  }}));}
  refresh() {
    if (this.refreshing) { this.refreshAgain = true; return; }
    this.refreshing = true;
    this.reconcilePage(3);
  }
  private reconcilePage(remaining: number) {
    const scan = this.catchUp;
    this.subscriptions.add(this.service.history(this.projectId, scan?.cursor).subscribe({
      next: page => {
        const head = scan ? scan.head : page.turns[0]?.requestId ?? null;
        const reachedKnownHistory = page.turns.some(turn => turn.requestId === this.historyHead);
        if (!this.historyLoaded) {
          this.cursor.set(page.nextCursor);
          this.historyLoaded = true;
          this.historyHead = head;
        } else if (page.nextCursor && !reachedKnownHistory) {
          // Keep the manual pagination cursor separate; resume this scan on the next tick.
          this.catchUp = { head, cursor: page.nextCursor };
        } else {
          this.historyHead = head;
          this.catchUp = null;
        }
        this.acceptTurns(page.turns);
        if (this.catchUp && remaining > 1) {
          this.reconcilePage(remaining - 1);
          return;
        }
        this.refreshing = false;
        this.error.set('');
        if (this.refreshAgain) { this.refreshAgain = false; this.refresh(); }
      },
      error: () => {
        this.refreshing = false;
        this.refreshAgain = false;
        this.error.set('No se pudo recuperar el historial. Se conservaron los mensajes visibles.');
      }
    }));
  }
  loadOlder(){const cursor=this.cursor();if(!cursor||this.loadingOlder())return;this.loadingOlder.set(true);this.subscriptions.add(this.service.history(this.projectId,cursor).subscribe({next:page=>{
    this.acceptTurns(page.turns);this.cursor.set(page.nextCursor);this.loadingOlder.set(false);
  },error:()=>{this.loadingOlder.set(false);this.error.set('No se pudieron cargar mensajes anteriores.');}}));}
  private persistUnconfirmed(): boolean {
    if(!this.storageKey)return true;
    try {
      if(this.unconfirmed.length)localStorage.setItem(this.storageKey,JSON.stringify(this.unconfirmed));
      else localStorage.removeItem(this.storageKey);
      return true;
    } catch {
      this.error.set('No se pudo conservar el identificador del envío en este navegador. Revisa el almacenamiento antes de reintentar.');
      return false;
    }
  }
  private restoreUnconfirmed() {
    const user=this.auth.currentUser()?.username;
    if(!user)return;
    this.storageKey='arqnova-ai-unconfirmed-v1:'+encodeURIComponent(user)+':'+encodeURIComponent(this.projectId);
    try {
      const pending=JSON.parse(localStorage.getItem(this.storageKey)??'[]');
      if(!Array.isArray(pending)||pending.length>10||pending.some(item=>!item
        ||typeof item.requestId!=='string'||!/^[A-Za-z0-9_-]{8,100}$/.test(item.requestId)
        ||!['ASK','REVIEW','ADVISE','MEDIATE','MODEL'].includes(item.mode)
        ||typeof item.instruction!=='string'||!item.instruction.trim()||item.instruction.length>4000))throw new Error();
      this.unconfirmed=pending;
      const latest=this.unconfirmed.at(-1);
      if(latest&&!this.instruction.trim()){this.mode=latest.mode;this.instruction=latest.instruction;}
      this.reconcileUnconfirmed();
    } catch {this.error.set('No se pudieron recuperar los envíos locales. Conserva tu instrucción antes de volver a intentar.');}
  }
  private reconcileUnconfirmed() {
    if(this.reconcilingSends||!this.unconfirmed.length)return;
    this.reconcilingSends=true;
    const pending=[...this.unconfirmed];
    let remaining=pending.length;
    for(const item of pending){
      this.subscriptions.add(this.service.get(this.projectId,item.requestId).subscribe({
        next:turn=>{
          this.acceptTurns([turn]);
          if(this.instruction.trim()===item.instruction&&this.mode===item.mode
            &&!this.unconfirmed.some(value=>value.requestId===item.requestId))this.instruction='';
          if(--remaining===0)this.reconcilingSends=false;
        },
        error:failure=>{
          if(--remaining===0)this.reconcilingSends=false;
          this.error.set(failure?.status===404
            ? 'El servidor todavía no registra este envío. Puedes enviarlo otra vez conservando su identificador.'
            : 'No se pudo confirmar el envío anterior. Recupera la conversación antes de reenviarlo.');
        }
      }));
    }
  }
  recover(){if(!this.enabled()){this.ngOnInit();return;}this.reconcileUnconfirmed();this.refresh();}
  prepareModeling(instruction: string) {
    if (!this.modelingEnabled() || this.sending() || this.instruction.trim()) return;
    this.mode = 'MODEL';
    this.instruction = instruction;
  }
  send(){if(this.sending()||!this.instruction.trim())return;
    if(this.mode==='MODEL'&&!this.modelingEnabled()){this.error.set('Tu acceso actual no permite modificar el diagrama. Recupera la conversación para comprobar el envío anterior.');return;}
    this.sending.set(true);this.error.set('');
    const content=JSON.stringify([this.mode,this.instruction.trim()]);
    const previous=this.unconfirmed.find(item=>item.mode===this.mode&&item.instruction===this.instruction.trim());
    if(previous){this.requestId=previous.requestId;this.requestContent=content;}
    if(!this.requestId||this.requestContent!==content){this.requestId=crypto.randomUUID();this.requestContent=content;}const id=this.requestId;
    if(!this.unconfirmed.some(item=>item.requestId===id)){
      if(this.unconfirmed.length>=10){this.sending.set(false);this.error.set('Recupera los envíos pendientes antes de enviar otra solicitud.');return;}
      this.unconfirmed.push({requestId:id,mode:this.mode,instruction:this.instruction.trim()});
    }
    if(!this.persistUnconfirmed()){this.sending.set(false);return;}
    this.subscriptions.add(this.service.send(this.projectId,id,this.mode,this.instruction).subscribe({next:turn=>{
      this.acceptTurns([turn]);if(JSON.stringify([this.mode,this.instruction.trim()])===content)this.instruction='';this.requestId=null;this.sending.set(false);this.refresh();
    },error:()=>{this.sending.set(false);this.error.set('No se confirmó el envío. Vuelve a enviar el mismo mensaje para comprobarlo sin duplicarlo.');}}));
  }
  failureText(code:string|null){switch(code){case'AI_MODEL_CONFLICT':return'El diagrama cambió o un elemento está en edición. Revisa la versión guardada antes de enviar otra solicitud.';case'AI_INVALID_OPERATIONS':return'La solicitud no produjo un lote UML válido. No se aplicaron cambios.';case'AI_CONTEXT_TOO_LARGE':return'El diagrama supera el contexto permitido para esta consulta. El modelo guardado se conserva.';case'AI_RATE_LIMITED':return'La IA alcanzó temporalmente su cuota. Intenta una nueva consulta más tarde.';case'AI_REQUEST_INTERRUPTED':return'La solicitud se interrumpió y no tiene un resultado guardado. Puedes enviar una nueva consulta.';case'AI_RECOVERY_EXHAUSTED':return'No se logró guardar un resultado tras los intentos de recuperación. La IA pudo haber procesado la solicitud; revisa el diagrama y el historial antes de enviar otra.';case'AI_QUEUE_BUSY':return'Hay varias solicitudes en espera. Intenta más tarde.';case'ACCESS_CHANGED':return'El acceso al proyecto cambió durante la solicitud.';default:return'El proveedor de IA no pudo completar esta respuesta. El diagrama no fue modificado.';}}
}
