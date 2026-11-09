import { describe,it,expect,vi } from 'vitest';
import { of } from 'rxjs';
import { AiConversationUseCases,mergeAiTurns } from './ai-conversation.use-cases';
import { AiConversationPort } from './ports/ai-conversation.port';
import { AiTurn } from '../domain/uml/ai-conversation.models';
const turn=(id:string,state:AiTurn['state'],at='2026-11-01T12:00:00Z'):AiTurn=>({requestId:id,projectId:'p',authorId:'a',authorName:'Noelia',mode:'ASK',instruction:'Pregunta',state,answer:null,errorCode:null,diagramVersion:3,createdAt:'2026-11-01T12:00:00Z',updatedAt:at});
describe('conversación compartida: reglas de aplicación',()=>{
 it('fusiona notificaciones repetidas sin duplicar mensajes',()=>{const t=turn('request-001','PENDING');expect(mergeAiTurns([t],[t])).toHaveLength(1);});
 it('una respuesta pendiente tardía no reemplaza el resultado final',()=>{
  const done=turn('request-001','COMPLETED','2026-11-01T12:00:01Z');
  expect(mergeAiTurns([done],[turn('request-001','PENDING')])[0].state).toBe('COMPLETED');
 });
 it('conserva los mensajes anteriores al actualizar la página reciente',()=>{expect(mergeAiTurns([turn('old','COMPLETED')],[turn('new','PENDING')])).toHaveLength(2);});
 it('mantiene el identificador de reintento y normaliza el mensaje',()=>{
  const send=vi.fn(()=>of(turn('request-001','PENDING')));const port={send} as unknown as AiConversationPort;
  new AiConversationUseCases(port).send('p','request-001','ASK','  Pregunta  ');
  expect(send).toHaveBeenCalledWith('p','request-001','ASK','Pregunta');
 });
 it('rechaza entradas inválidas antes de utilizar la API',()=>{
  const send=vi.fn();const cases=new AiConversationUseCases({send} as unknown as AiConversationPort);
  expect(()=>cases.send('p','request-001','ASK',' '.repeat(3))).toThrow();expect(send).not.toHaveBeenCalled();
 });
});
