import { describe,it,expect } from 'vitest';
import { planAiBatch } from './plan-ai-batch';
import { DEFAULT_UML_MODEL } from './uml-class-diagram.models';
import { UmlAiCommand } from './ai.models';
const graph=()=>({...DEFAULT_UML_MODEL,classes:DEFAULT_UML_MODEL.classes.slice(0,2),relations:[]});
const command=(operation:any,parameters:any,targetId?:string):UmlAiCommand=>({status:'READY',operation,parameters,targetId,message:'test'});
describe('lotes IA completos',()=>{
 it('no cambia original ni aplica prefijo cuando falla la segunda operación',()=>{
  const original=graph(),before=JSON.stringify(original);
  expect(()=>planAiBatch(original,[command('ATTRIBUTE_CREATE',{classId:original.classes[0].id,name:'email',type:'String'}),command('METHOD_CREATE',{classId:'missing',name:'guardar'})],()=>crypto.randomUUID())).toThrow();
  expect(JSON.stringify(original)).toBe(before);
 });
 it('crea clase, atributo y método referenciados en un único resultado',()=>{
  const original=graph();const result=planAiBatch(original,[command('CLASS_CREATE',{id:'nueva',name:'Nueva'}),command('ATTRIBUTE_CREATE',{classId:'nueva',name:'email',type:'String'}),command('METHOD_CREATE',{classId:'nueva',name:'guardar'})],()=>crypto.randomUUID());
  expect(original.classes).toHaveLength(2);expect(result.classes.at(-1)?.attributes[0].name).toBe('email');expect(result.classes.at(-1)?.methods[0].name).toBe('guardar');
 });
 it('rechaza ciclos y relaciones idénticas conservando asociaciones distintas',()=>{
  const original=graph(),[a,b]=original.classes;
  for(const type of ['inheritance','composition'])expect(()=>planAiBatch(original,[command('RELATION_CREATE',{sourceId:a.id,targetId:b.id,type}),command('RELATION_CREATE',{sourceId:b.id,targetId:a.id,type})],()=>crypto.randomUUID())).toThrow();
  expect(()=>planAiBatch(original,[command('RELATION_CREATE',{sourceId:a.id,targetId:b.id}),command('RELATION_CREATE',{sourceId:a.id,targetId:b.id})],()=>crypto.randomUUID())).toThrow();
  expect(planAiBatch(original,[command('RELATION_CREATE',{sourceId:a.id,targetId:b.id,label:'uno'}),command('RELATION_CREATE',{sourceId:a.id,targetId:b.id,label:'otro'})],()=>crypto.randomUUID()).relations).toHaveLength(2);
 });
});
