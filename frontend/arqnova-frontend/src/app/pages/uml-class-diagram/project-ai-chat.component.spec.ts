import { TestBed } from '@angular/core/testing';
import { describe,it,expect,vi,afterEach } from 'vitest';
import { of,Subject,throwError } from 'rxjs';
import { ProjectAiChatComponent } from './project-ai-chat.component';
import { AiConversationService } from './ai-conversation.service';
describe('panel de conversación del proyecto',()=>{
 afterEach(()=>TestBed.resetTestingModule());
 function setup(enabled=true){
  const service={status:vi.fn(()=>of({enabled})),history:vi.fn(()=>of({turns:[],nextCursor:null})),watch:vi.fn(()=>new Subject()),send:vi.fn(()=>throwError(()=>new Error('network')))};
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
});
