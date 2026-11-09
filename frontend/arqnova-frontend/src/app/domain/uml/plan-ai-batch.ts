import { UmlDiagramModel,UmlClassNode,UmlRelation,UmlAttribute,UmlMethod } from './uml-class-diagram.models';
import { UmlAiCommand } from './ai.models';
import { createAssociationClass,normalizedName,validMultiplicity } from './diagram-rules';

/** Pure preparation: never changes the supplied model, sends events or persists a prefix. */
export function planAiBatch(original:UmlDiagramModel,commands:UmlAiCommand[],id:(prefix:string)=>string):UmlDiagramModel {
  if(!commands.length||commands.length>50)throw new Error('Indica entre 1 y 50 operaciones UML.');
  const model:UmlDiagramModel=JSON.parse(JSON.stringify(original));
  const text=(value:unknown)=>{if(typeof value!=='string'||!value.trim())throw new Error('Falta un nombre o identificador UML.');return value.trim();};
  const find=<T extends {id:string}>(rows:T[],key:unknown):T=>{const row=rows.find(v=>v.id===key);if(!row)throw new Error('Elemento UML inexistente.');return row;};
  const unique=(rows:{id:string;name:string}[])=>{if(new Set(rows.map(r=>r.id)).size!==rows.length||new Set(rows.map(r=>normalizedName(r.name))).size!==rows.length)throw new Error('Identificador o nombre UML duplicado.');};
  const removeClasses=(initial:string[])=>{
    const removed=new Set(initial);let expanded:boolean;
    do{expanded=false;for(const r of model.relations)if(removed.has(r.sourceId)||removed.has(r.targetId))if(r.associationClassId&&!removed.has(r.associationClassId)){removed.add(r.associationClassId);expanded=true;}}while(expanded);
    model.classes=model.classes.filter(c=>!removed.has(c.id));model.relations=model.relations.filter(r=>!removed.has(r.sourceId)&&!removed.has(r.targetId));
    for(const r of model.relations)if(r.associationClassId&&removed.has(r.associationClassId))delete r.associationClassId;
  };
  for(const command of commands){
    if(command.status!=='READY')throw new Error('Operación UML sin confirmar.');
    const p=command.parameters??{},target=command.targetId;
    const allowed:Record<string,string[]>={
      CLASS_CREATE:['id','name','attributes','methods','description','stereotype','x','y','color','responsibilities'],
      CLASS_UPDATE:['name','description','stereotype','x','y','color','responsibilities'],CLASS_DELETE:['className','name'],
      ATTRIBUTE_CREATE:['id','classId','className','name','type','isPrimaryKey'],ATTRIBUTE_UPDATE:['classId','className','attributeName','name','type','isPrimaryKey'],ATTRIBUTE_DELETE:['classId','className','attributeName'],
      METHOD_CREATE:['id','classId','className','name','parameters','returnType'],METHOD_UPDATE:['classId','className','methodName','name','parameters','returnType'],METHOD_DELETE:['classId','className','methodName'],
      RELATION_CREATE:['id','sourceId','targetId','sourceName','targetName','type','label','sourceMultiplicity','targetMultiplicity','associationClassName'],
      RELATION_UPDATE:['sourceId','targetId','sourceName','targetName','type','label','sourceMultiplicity','targetMultiplicity','associationClassName'],RELATION_DELETE:[]
    };
    if(Object.keys(p).some(key=>!allowed[command.operation??'']?.includes(key)))throw new Error('Parámetro UML no admitido.');
    switch(command.operation){
      case 'CLASS_CREATE':{
        const n=model.classes.length;
        const c:UmlClassNode={id:p['id']||target||id('class'),name:text(p['name']),description:'',stereotype:'Entidad',attributes:[],methods:[],responsibilities:[],x:70+(n%4)*350,y:70+Math.floor(n/4)*300,color:['#1e3a8a','#2563eb','#7c3aed','#b45309'][n%4]};
        c.attributes=(p['attributes']??[]).map((a:any)=>({id:a.id||id('attr'),name:text(a.name),type:a.type??'String',isPrimaryKey:a.isPrimaryKey===true}));
        c.methods=(p['methods']??[]).map((m:any)=>({id:m.id||id('method'),name:text(m.name),parameters:m.parameters??'',returnType:m.returnType??'void'}));
        for(const key of ['description','stereotype','x','y','color','responsibilities'] as const)if(key in p)(c as any)[key]=p[key];
        model.classes.push(c);break;
      }
      case 'CLASS_UPDATE':{
        const c=find(model.classes,target);
        for(const key of Object.keys(p)){if(!['name','description','stereotype','x','y','color','responsibilities'].includes(key))throw new Error('Parámetro de clase no admitido.');(c as any)[key]=p[key];}break;
      }
      case 'CLASS_DELETE':find(model.classes,target);removeClasses([target!]);break;
      case 'ATTRIBUTE_CREATE':case 'ATTRIBUTE_UPDATE':case 'ATTRIBUTE_DELETE':case 'METHOD_CREATE':case 'METHOD_UPDATE':case 'METHOD_DELETE':{
        const c=find(model.classes,p['classId']),method=command.operation.startsWith('METHOD_');
        const rows:(UmlAttribute|UmlMethod)[]=method?c.methods:c.attributes;
        if(command.operation.endsWith('_CREATE')){
          const member={id:target||p['id']||id(method?'method':'attr'),name:text(p['name']),...(method?{parameters:p['parameters']??'',returnType:p['returnType']??'void'}:{type:p['type']??'String',isPrimaryKey:p['isPrimaryKey']===true})};
          rows.push(member);
        }else{
          const member=find(rows,target);
          if(command.operation.endsWith('_DELETE'))rows.splice(rows.indexOf(member),1);
          else for(const key of method?['name','parameters','returnType']:['name','type','isPrimaryKey'])if(key in p)(member as any)[key]=p[key];
        }break;
      }
      case 'RELATION_CREATE':case 'RELATION_UPDATE':{
        const r:UmlRelation=command.operation==='RELATION_CREATE'?{id:target||p['id']||id('rel'),sourceId:'',targetId:'',type:'association',label:'',sourceMultiplicity:'1',targetMultiplicity:'0..*'}:find(model.relations,target);
        for(const key of ['sourceId','targetId','type','label','sourceMultiplicity','targetMultiplicity'] as const)if(key in p)(r as any)[key]=p[key];
        find(model.classes,r.sourceId);find(model.classes,r.targetId);
        if(command.operation==='RELATION_CREATE')model.relations.push(r);
        if(!r.associationClassId&&p['associationClassName']){const found=model.classes.find(c=>normalizedName(c.name)===normalizedName(p['associationClassName']));if(found){if([r.sourceId,r.targetId].includes(found.id))throw new Error('Clase de asociación inválida.');r.associationClassId=found.id;found.stereotype='Clase de asociación';}}
        const assoc=createAssociationClass(r,model.classes,{cardWidth:270,canvasWidth:1500,canvasHeight:900},id,p['associationClassName']??'');if(assoc)model.classes.push(assoc);break;
      }
      case 'RELATION_DELETE':{const r=find(model.relations,target);model.relations=model.relations.filter(v=>v!==r);if(r.associationClassId)removeClasses([r.associationClassId]);break;}
      default:throw new Error('Operación UML no admitida.');
    }
    unique(model.classes);for(const c of model.classes){unique(c.attributes);unique(c.methods);}
  }
  const signatures=new Set<string>(),relationIds=new Set<string>();
  for(const r of model.relations){
    find(model.classes,r.sourceId);find(model.classes,r.targetId);
    if(!['association','aggregation','composition','inheritance','dependency'].includes(r.type)||(r.sourceMultiplicity!==''&&!validMultiplicity(r.sourceMultiplicity))||(r.targetMultiplicity!==''&&!validMultiplicity(r.targetMultiplicity)))throw new Error('Relación o multiplicidad inválida.');
    for(const value of [r.sourceMultiplicity,r.targetMultiplicity])if(/^\d+\.\.\d+$/.test(value)){const [a,b]=value.split('..').map(Number);if(a>b)throw new Error('Multiplicidad invertida.');}
    let s=r.sourceId,t=r.targetId,sm=r.sourceMultiplicity,tm=r.targetMultiplicity;
    if(r.type==='association'&&s>t){[s,t]=[t,s];[sm,tm]=[tm,sm];}
    const key=JSON.stringify([r.type,s,t,r.label.trim(),sm,tm]);
    if(signatures.has(key)||relationIds.has(r.id))throw new Error('Relación duplicada.');signatures.add(key);relationIds.add(r.id);
  }
  for(const type of ['inheritance','composition']){
    const visited=new Set<string>(),visiting=new Set<string>();
    const visit=(current:string)=>{if(visited.has(current))return;if(visiting.has(current))throw new Error('Ciclo UML inválido.');visiting.add(current);for(const r of model.relations)if(r.type===type&&r.sourceId===current)visit(r.targetId);visiting.delete(current);visited.add(current);};
    model.classes.forEach(c=>visit(c.id));
  }
  return model;
}
