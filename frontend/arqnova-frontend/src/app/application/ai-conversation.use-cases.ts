import { AiConversationPort } from './ports/ai-conversation.port';
import { AiConversationMode,AiTurn } from '../domain/uml/ai-conversation.models';
export class AiConversationUseCases {
  constructor(private readonly port:AiConversationPort){}
  status(project:string){return this.port.status(project);}
  history(project:string,cursor?:string){return this.port.history(project,cursor);}
  get(project:string,requestId:string){return this.port.get(project,requestId);}
  send(project:string,requestId:string,mode:AiConversationMode,instruction:string){
    if(!instruction.trim()||instruction.length>4000)throw new Error('Escribe un mensaje de hasta 4000 caracteres.');
    return this.port.send(project,requestId,mode,instruction.trim());
  }
  watch(project:string){return this.port.watch(project);}
}
export function mergeAiTurns(existing:AiTurn[],incoming:AiTurn[]):AiTurn[]{
  const turns=new Map(existing.map(turn=>[turn.requestId,turn]));
  for(const turn of incoming){
    const previous=turns.get(turn.requestId);
    if(!previous||Date.parse(turn.updatedAt)>Date.parse(previous.updatedAt)||
       (turn.updatedAt===previous.updatedAt&&(previous.state==='PENDING'||turn.state!=='PENDING')))turns.set(turn.requestId,turn);
  }
  return [...turns.values()].sort((a,b)=>a.createdAt.localeCompare(b.createdAt)||a.requestId.localeCompare(b.requestId));
}
