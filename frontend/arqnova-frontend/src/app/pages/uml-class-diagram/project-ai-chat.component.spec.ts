import { AuthService } from '../../auth/auth.service';
import { TestBed } from '@angular/core/testing';
import { describe,it,expect,vi,afterEach,beforeEach } from 'vitest';
import { of,Subject,throwError,Observable } from 'rxjs';
import { ProjectAiChatComponent } from './project-ai-chat.component';
import { AiConversationChange, AiConversationPage, AiTurn } from '../../domain/uml/ai-conversation.models';
import { AiConversationService } from './ai-conversation.service';
describe('panel de conversación del proyecto',()=>{
 beforeEach(()=>localStorage.clear());
 afterEach(()=>TestBed.resetTestingModule());
 function setup(enabled=true,modes:string[]=['ASK','REVIEW'],initial:AiConversationPage={turns:[],nextCursor:null},user='ana',restored?:AiTurn){
  const changes = new Subject<AiConversationChange>();
  const service={status:vi.fn(()=>of({enabled,modes})),history:vi.fn((_project:string,_cursor?:string)=>of(initial)),watch:vi.fn(()=>changes),get:vi.fn((_p:string,_id:string):Observable<AiTurn>=>restored?of(restored):throwError(()=>({status:404}))),send:vi.fn(()=>throwError(()=>new Error('network')))};
  TestBed.configureTestingModule({imports:[ProjectAiChatComponent],providers:[{provide:AiConversationService,useValue:service},{provide:AuthService,useValue:{currentUser:()=>({username:user})}}]});
  const fixture=TestBed.createComponent(ProjectAiChatComponent);fixture.componentRef.setInput('projectId','p');fixture.detectChanges();return{fixture,service,changes,component:fixture.componentInstance};
 }
 it('mantiene disponible el editor cuando el módulo nuevo está apagado',()=>{
  const {fixture,service}=setup(false);expect(fixture.nativeElement.textContent).toContain('todavía no está habilitada');expect(service.history).not.toHaveBeenCalled();
 });
 it('reutiliza la solicitud si falla la confirmación del mismo mensaje',()=>{
  const {component,service}=setup();component.instruction='Pregunta';component.send();component.send();
  expect(service.send.mock.calls).toHaveLength(2);expect(service.send.mock.calls[0]).toEqual(service.send.mock.calls[1]);
 });
 it('un mensaje diferente recibe otro identificador',()=>{
  const {component,service}=setup();component.instruction='Primera';component.send();component.instruction='Segunda';component.send();
  expect((service.send.mock.calls[0] as unknown[])[1]).not.toBe((service.send.mock.calls[1] as unknown[])[1]);
 });
 it('conserva el borrador nuevo si la confirmación del envío anterior llega tarde',()=>{
  const {component,service}=setup();const response=new Subject<any>();
  service.send.mockReturnValue(response as any);
  component.instruction='Primera pregunta';component.send();
  component.instruction='Segunda pregunta';
  response.next({requestId:'request-first',state:'PENDING',createdAt:'2026-11-02T12:00:00Z',updatedAt:'2026-11-02T12:00:00Z'});
  expect(component.instruction).toBe('Segunda pregunta');expect(component.sending()).toBe(false);
 });
 it('limpia el texto confirmado cuando el usuario no empezó otro borrador',()=>{
  const {component,service}=setup();const response=new Subject<any>();
  service.send.mockReturnValue(response as any);
  component.instruction='Primera pregunta';component.send();
  response.next({requestId:'request-first',state:'PENDING',createdAt:'2026-11-02T12:00:00Z',updatedAt:'2026-11-02T12:00:00Z'});
  expect(component.instruction).toBe('');
 });
 it('ofrece modelado únicamente cuando el servidor lo habilita',()=>{
  const {fixture,component}=setup(true,['ASK','REVIEW','MODEL']);
  expect(fixture.nativeElement.textContent).toContain('Modificar diagrama guardado');
  component.mode='MODEL';fixture.changeDetectorRef.markForCheck();fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('para todo el equipo');
 });
 it('avisa al lienzo cuando el sondeo confirma el modelado sin evento WebSocket',()=>{
  vi.useFakeTimers();
  try {
   const {component,service,fixture}=setup(true,['MODEL']);
   const pending: AiTurn={requestId:'request-model',projectId:'p',authorId:'u',authorName:'Usuario',
    mode:'MODEL',instruction:'Cambia la multiplicidad',state:'PENDING',answer:null,errorCode:null,
    diagramVersion:0,createdAt:'2026-11-09T10:00:00Z',updatedAt:'2026-11-09T10:00:00Z'};
   service.send.mockReturnValue(of(pending) as any);
   service.get.mockReturnValue(of({...pending,state:'COMPLETED',diagramVersion:85}));
   const completed=vi.fn();component.modelCompleted.subscribe(completed);
   component.mode='MODEL';component.instruction=pending.instruction;component.send();
   expect(completed).not.toHaveBeenCalled();
   vi.advanceTimersByTime(4000);
   expect(completed).toHaveBeenCalledExactlyOnceWith(85);
   fixture.destroy();
  } finally {vi.useRealTimers();}
 });
 const turn = (requestId: string, day: number): AiTurn => ({
  requestId, projectId:'p', authorId:'u', authorName:'Ana', mode:'ASK', instruction:requestId,
  state:'COMPLETED', answer:null, errorCode:null, diagramVersion:1,
  createdAt:`2026-11-${day}T10:00:00Z`, updatedAt:`2026-11-${day}T10:01:00Z`
 });
 it('recupera mensajes sin WebSocket con paginación acotada y conserva el cursor del usuario',()=>{
  vi.useFakeTimers();
  try {
   const base=turn('base',10), a=turn('a',11), b=turn('b',12), c=turn('c',13), d=turn('d',14);
   const {component,service,fixture}=setup(true,['ASK'],{turns:[base],nextCursor:'manual'});
   service.history
    .mockReturnValueOnce(of({turns:[d],nextCursor:'gap-1'}))
    .mockReturnValueOnce(of({turns:[c],nextCursor:'gap-2'}))
    .mockReturnValueOnce(of({turns:[b],nextCursor:'gap-3'}))
    .mockReturnValueOnce(of({turns:[a,base],nextCursor:'manual'}));

   vi.advanceTimersByTime(15_000);
   expect(service.history).toHaveBeenCalledTimes(4);
   vi.advanceTimersByTime(15_000);
   expect(service.history).toHaveBeenLastCalledWith('p','gap-3');
   expect(component.turns().map(item=>item.requestId)).toEqual(['base','a','b','c','d']);
   expect(component.cursor()).toBe('manual');
   fixture.destroy();
   vi.advanceTimersByTime(15_000);
   expect(service.history).toHaveBeenCalledTimes(5);
  } finally {vi.useRealTimers();}
 });
 it('reconcilia al reconectar y deduplica un mensaje que también llegó en vivo',()=>{
  const base=turn('base',10), latest=turn('latest',11);
  const {component,service,changes}=setup(true,['ASK'],{turns:[base],nextCursor:null});
  service.get.mockReturnValue(of(latest));
  changes.next({projectId:'p',requestId:latest.requestId,state:'COMPLETED'});
  service.history.mockReturnValue(of({turns:[latest,base],nextCursor:null}));
  changes.next({projectId:'p',state:'CONNECTED'});
  expect(service.history).toHaveBeenCalledTimes(2);
  expect(component.turns().map(item=>item.requestId)).toEqual(['base','latest']);
 });
 it('oculta Modelado si el servidor solo anuncia modos de lectura',()=>{
  const {fixture}=setup(true,['ASK','REVIEW','ADVISE','MEDIATE']);
  expect(fixture.nativeElement.querySelector('option[value="MODEL"]')).toBeNull();
  expect(fixture.nativeElement.querySelector('option[value="MEDIATE"]')).not.toBeNull();
 });
 it('envía asesoramiento y mediación como modos separados',()=>{
  const {fixture,component,service}=setup(true,['ASK','REVIEW','ADVISE','MEDIATE']);
  expect(fixture.nativeElement.textContent).toContain('Pedir asesoramiento');
  expect(fixture.nativeElement.textContent).toContain('Mediar entre propuestas');
  component.mode='ADVISE';component.instruction='Evalúa esta opción';component.send();
  component.mode='MEDIATE';component.instruction='Compara ambas propuestas';component.send();
  expect(service.send).toHaveBeenNthCalledWith(1,'p',expect.any(String),'ADVISE','Evalúa esta opción');
  expect(service.send).toHaveBeenNthCalledWith(2,'p',expect.any(String),'MEDIATE','Compara ambas propuestas');
 });
 it('prepara una orden para Modelado y espera el envío explícito',()=>{
  const {component,service}=setup(true,['ASK','MODEL']);
  component.prepareModeling('Agrega NotaVenta a Venta');
  expect(component.mode).toBe('MODEL');
  expect(component.instruction).toBe('Agrega NotaVenta a Venta');
  expect(service.send).not.toHaveBeenCalled();
  component.prepareModeling('Otra instrucción');
  expect(component.instruction).toBe('Agrega NotaVenta a Venta');
 });
 it('recupera el ID no confirmado tras recargar y lo reutiliza si todavía no existe en servidor',()=>{
  const first=setup();first.component.instruction='Pregunta pendiente';first.component.send();
  const id=(first.service.send.mock.calls[0] as unknown[])[1];
  first.fixture.destroy();TestBed.resetTestingModule();
  const second=setup();
  expect(second.component.instruction).toBe('Pregunta pendiente');
  expect(second.service.get).toHaveBeenCalledWith('p',id);
  second.component.send();
  expect(second.service.send).toHaveBeenCalledWith('p',id,'ASK','Pregunta pendiente');
 });
 it('reconcilia el envío aceptado aunque se haya perdido su confirmación HTTP',()=>{
  const first=setup();first.component.instruction='Pregunta pendiente';first.component.send();
  const id=(first.service.send.mock.calls[0] as unknown[])[1] as string;
  first.fixture.destroy();TestBed.resetTestingModule();
  const restored={...turn(id,11),authorId:'ana',instruction:'Pregunta pendiente'};
  const second=setup(true,['ASK'],{turns:[],nextCursor:null},'ana',restored);
  expect(second.component.instruction).toBe('');
  expect(second.component.turns()).toContainEqual(restored);
  expect(second.service.send).not.toHaveBeenCalled();
  expect(localStorage.getItem('arqnova-ai-unconfirmed-v1:ana:p')).toBeNull();
 });
 it('aisla los envíos locales por usuario y proyecto',()=>{
  localStorage.setItem('arqnova-ai-unconfirmed-v1:otra:p',JSON.stringify([{requestId:'request-other',mode:'ASK',instruction:'Privado'}]));
  localStorage.setItem('arqnova-ai-unconfirmed-v1:ana:otro',JSON.stringify([{requestId:'request-other',mode:'ASK',instruction:'Otro proyecto'}]));
  const {component,service}=setup();
  expect(component.instruction).toBe('');
  expect(service.get).not.toHaveBeenCalled();
 });
 it('muestra el fallo de sondeo y permite recuperar la conversación',()=>{
  vi.useFakeTimers();
  try {
   const {component,service,fixture}=setup();
   const pending={...turn('pending-request',11),state:'PENDING' as const,authorId:'ana'};
   component.turns.set([pending]);
   service.get.mockReturnValue(throwError(()=>new Error('sin conexión')));
   vi.advanceTimersByTime(4000);
   fixture.detectChanges();
   expect(component.error()).toContain('desactualizado');
   expect(fixture.nativeElement.textContent).toContain('Recuperar conversación');
   component.recover();
   expect(service.history).toHaveBeenCalledTimes(2);
   fixture.destroy();
  } finally {vi.useRealTimers();}
 });
 it('muestra un conflicto sin afirmar que el diagrama fue modificado',()=>{
  const {component}=setup();expect(component.failureText('AI_MODEL_CONFLICT')).toContain('El diagrama cambió');
 });
});
