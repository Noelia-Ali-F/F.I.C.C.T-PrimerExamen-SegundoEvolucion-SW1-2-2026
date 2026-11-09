import { TestBed } from '@angular/core/testing';
import { describe,it,expect,vi,afterEach } from 'vitest';
import { of,Subject,throwError } from 'rxjs';
import { ProjectAiChatComponent } from './project-ai-chat.component';
import { AiTurn } from '../../domain/uml/ai-conversation.models';
import { AiConversationService } from './ai-conversation.service';
describe('panel de conversación del proyecto',()=>{
 afterEach(()=>TestBed.resetTestingModule());
 function setup(enabled=true,modes:string[]=['ASK','REVIEW']){
  const service={status:vi.fn(()=>of({enabled,modes})),history:vi.fn(()=>of({turns:[],nextCursor:null})),watch:vi.fn(()=>new Subject()),get:vi.fn(),send:vi.fn(()=>throwError(()=>new Error('network')))};
  TestBed.configureTestingModule({imports:[ProjectAiChatComponent],providers:[{provide:AiConversationService,useValue:service}]});
  const fixture=TestBed.createComponent(ProjectAiChatComponent);fixture.componentRef.setInput('projectId','p');fixture.detectChanges();return{fixture,service,component:fixture.componentInstance};
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
 it('muestra un conflicto sin afirmar que el diagrama fue modificado',()=>{
  const {component}=setup();expect(component.failureText('AI_MODEL_CONFLICT')).toContain('El diagrama cambió');
 });
});
