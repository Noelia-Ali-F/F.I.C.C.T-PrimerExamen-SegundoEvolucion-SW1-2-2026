import { Injectable } from '@angular/core';
import { UmlDiagramModel, UmlRelationType } from './uml-class-diagram.models';

export interface XmiPreview { model: UmlDiagramModel; warnings: string[]; }
const XMI = 'http://schema.omg.org/spec/XMI/2.1';
const ARQ = 'urn:arqnova:uml';
@Injectable({providedIn:'root'})
export class UmlXmiService {
  decode(buffer:ArrayBuffer):string {
    const bytes=new Uint8Array(buffer);
    const bomEncoding=bytes[0]===0xff&&bytes[1]===0xfe?'utf-16le':bytes[0]===0xfe&&bytes[1]===0xff?'utf-16be':null;
    const header=new TextDecoder(bomEncoding||'utf-8').decode(bytes.slice(0,512));
    const encoding=bomEncoding||header.match(/encoding\s*=\s*["']([^"']+)["']/i)?.[1]||'utf-8';
    try{return new TextDecoder(encoding,{fatal:true}).decode(bytes);}catch{throw new Error('No se pudo leer la codificación XML declarada. Exporta el archivo como UTF-8 o Windows-1252.');}
  }
  parse(xml:string):XmiPreview {
    if(xml.length>10*1024*1024 || /<!DOCTYPE|<!ENTITY/i.test(xml)) throw new Error('XMI inválido: tamaño excesivo o declaraciones externas no permitidas.');
    const doc=new DOMParser().parseFromString(xml,'application/xml');
    if(doc.getElementsByTagName('parsererror').length || doc.documentElement.localName!=='XMI' || !/XMI/i.test(doc.documentElement.namespaceURI||'')) throw new Error('El archivo no contiene XML/XMI válido.');
    const version=Array.from(doc.documentElement.attributes).find(a=>a.localName==='version'&&/XMI/i.test(a.namespaceURI||''))?.value||doc.documentElement.getAttribute('version');
    if(version&&!['2.1','2.4','2.4.1','2.5','2.5.1'].includes(version)) throw new Error('Versión no soportada. Exporta desde Enterprise Architect como XMI 2.1 / UML 2.');
    const documentElements=Array.from(doc.getElementsByTagName('*'));
    const inExtension=(e:Element)=>{for(let p:Element|null=e;p;p=p.parentElement)if(p.localName==='Extension'&&/XMI/i.test(p.namespaceURI||''))return true;return false;};
    // EA repeats model IDs in its graphical metadata. These are not UML definitions.
    const all=documentElements.filter(e=>!inExtension(e));
    const attr=(e:Element,key:string)=>Array.from(e.attributes).find(a=>a.localName===key&&/XMI/i.test(a.namespaceURI||''))?.value||e.getAttribute('xmi:'+key)||'';
    const id=(e:Element)=>e.getAttributeNS(ARQ,'internalId')||attr(e,'id');
    const kind=(e:Element)=>{const value=(attr(e,'type')||e.localName).split(':').pop();return value==='generalization'?'Generalization':value;};
    const children=(e:Element,name:string)=>Array.from(e.children).filter(c=>c.localName===name);
    const byId=new Map<string,Element>();
    for(const e of all){const key=attr(e,'id');if(key){if(byId.has(key))throw new Error('XMI inválido: IDs duplicados.');byId.set(key,e);}}
    // EA stores some primitive definitions only in its extension.
    for(const e of documentElements.filter(e=>inExtension(e)&&['PrimitiveType','DataType'].includes(kind(e)!))){const key=attr(e,'id');if(key&&!byId.has(key))byId.set(key,e);}
    const warnings:string[]=[];
    const metadata=(e:Element)=>documentElements.find(m=>inExtension(m)&&attr(m,'idref')===attr(e,'id')&&['element','attribute','operation'].includes(m.localName));
    const classes=all.filter(e=>['Class','AssociationClass'].includes(kind(e)!)&&!!attr(e,'id'));
    const classIds=new Map(classes.map(e=>[attr(e,'id'),id(e)]));
    const type=(e:Element)=>{const ref=e.getAttribute('type')||attr(children(e,'type')[0]||e,'idref');const nested=children(e,'type')[0];const href=nested?.getAttribute('href')||e.getAttribute('href');const resolved=byId.get(ref)?.getAttribute('name')||(href?decodeURIComponent(href.split('#').pop()||''):'')||children(metadata(e)||e,'properties')[0]?.getAttribute('type')||'';if(ref&&!resolved)warnings.push(`Tipo externo no resuelto para ${e.getAttribute('name')}; se dejó sin definir.`);return resolved;};
    const pk=(e:Element)=>{const meta=metadata(e);return e.getAttribute('isID')==='true'||e.getAttributeNS(ARQ,'isPrimaryKey')==='true'||[e,meta].filter((value):value is Element=>!!value).some(parent=>Array.from(parent.getElementsByTagName('*')).some(t=>['tag','TaggedValue'].includes(t.localName)&&(t.getAttribute('name')||t.getAttribute('tag'))==='isPrimaryKey'&&t.getAttribute('value')==='true'));};
    const methods=(e:Element)=>children(e,'ownedOperation').map((o,i)=>{const params=children(o,'ownedParameter');const returns=params.filter(p=>p.getAttribute('direction')==='return');if(returns.length>1)warnings.push(`La operación ${o.getAttribute('name')} tiene varios retornos; revisa el Inspector.`);return{id:id(o)||`${id(e)}-method-${i}`,name:o.getAttribute('name')||'operacion',parameters:params.length?params.filter(p=>p.getAttribute('direction')!=='return').map(p=>{const direction=p.getAttribute('direction');return `${direction&&direction!=='in'?direction+' ':''}${p.getAttribute('name')||'parametro'}: ${type(p)}`;}).join(', '):o.getAttributeNS(ARQ,'parameters')||'',returnType:returns.length?type(returns[0])||'void':o.getAttributeNS(ARQ,'returnType')||'void'};});
    const model:UmlDiagramModel={format:'uml-class-diagram',version:2,title:documentElements.find(e=>inExtension(e)&&e.localName==='diagram')?.getElementsByTagName('properties')[0]?.getAttribute('name')||all.find(e=>kind(e)==='Model')?.getAttribute('name')||'Modelo XMI',classes:[],relations:[]};
    classes.forEach((e,n)=>{
      if(!e.getAttribute('name')?.trim())throw new Error('Una clase XMI no tiene nombre.');
      const attributes=children(e,'ownedAttribute').sort((a,b)=>Number(children(metadata(a)||a,'containment')[0]?.getAttribute('position')??0)-Number(children(metadata(b)||b,'containment')[0]?.getAttribute('position')??0)).filter(a=>!a.getAttribute('association')).map((a,i)=>{if(!a.getAttribute('name')?.trim())throw new Error('Un atributo no tiene nombre.');return{id:id(a)||`${id(e)}-attribute-${i}`,name:a.getAttribute('name')!,type:type(a),isPrimaryKey:pk(a)};});
      const graphic=documentElements.find(g=>inExtension(g)&&g.getAttribute('subject')===attr(e,'id')&&g.hasAttribute('geometry'));
      const geometry=graphic?.getAttribute('geometry')||'';
      const position=(key:string,fallback:number)=>{const value=e.getAttributeNS(ARQ,key);return value!==null&&Number.isFinite(Number(value))?Number(value):Number(geometry.match(new RegExp(`${key==='x'?'Left':'Top'}=(-?\\d+(?:\\.\\d+)?);`))?.[1]??fallback);};
      model.classes.push({id:id(e),name:e.getAttribute('name')!,attributes,methods:methods(e),description:e.getAttributeNS(ARQ,'description')||'',stereotype:e.getAttributeNS(ARQ,'stereotype')||children(metadata(e)||e,'properties')[0]?.getAttribute('stereotype')||'Entidad',responsibilities:[],x:position('x',70+n%4*350),y:position('y',70+Math.floor(n/4)*300),color:e.getAttributeNS(ARQ,'color')||this.eaColor(graphic?.getAttribute('style')||'')});
    });
    if(!model.classes.length)throw new Error('No se encontraron clases UML 2 soportadas.');
    const multiplicity=(e:Element)=>{const rawLower=children(e,'lowerValue')[0]?.getAttribute('value')??'1',rawUpper=children(e,'upperValue')[0]?.getAttribute('value')??'1';const lower=rawLower==='-1'?(rawUpper==='-1'?'*':'0'):rawLower,upper=rawUpper==='-1'?'*':rawUpper;const value=lower===upper?lower:`${lower}..${upper}`;if(!/^(\*|\d+|\d+\.\.(\d+|\*))$/.test(value))throw new Error('Multiplicidad XMI inválida.');return value;};
    for(const e of all.filter(e=>['Association','AssociationClass','Dependency','Generalization'].includes(kind(e)!))) {
      let source='',target='',sm='1',tm='1',relationType:UmlRelationType='association';
      if(['Association','AssociationClass'].includes(kind(e)!)) {
        const refs=(e.getAttribute('memberEnd')||'').trim().split(/\s+/).filter(Boolean);
        if(!refs.length)refs.push(...children(e,'memberEnd').map(end=>attr(end,'idref')).filter(Boolean));
        const ends=refs.length?refs.map(r=>byId.get(r)).filter((a):a is Element=>!!a):children(e,'ownedEnd');
        if(ends.length!==2){warnings.push('Se omitió una asociación n-aria o con extremos incompletos.');continue;}
        // The existing editor renders the aggregate/composite diamond at source.
        if(['composite','shared'].includes(ends[1].getAttribute('aggregation')||'')&&!['composite','shared'].includes(ends[0].getAttribute('aggregation')||''))ends.reverse();
        source=ends[0].getAttribute('type')||attr(children(ends[0],'type')[0]||ends[0],'idref');target=ends[1].getAttribute('type')||attr(children(ends[1],'type')[0]||ends[1],'idref');sm=multiplicity(ends[0]);tm=multiplicity(ends[1]);
        const aggregation=ends.map(a=>a.getAttribute('aggregation'));relationType=aggregation.includes('composite')?'composition':aggregation.includes('shared')?'aggregation':'association';
      } else if(kind(e)==='Dependency'){source=e.getAttribute('client')||attr(children(e,'client')[0]||e,'idref');target=e.getAttribute('supplier')||attr(children(e,'supplier')[0]||e,'idref');relationType='dependency';}
      else{source=attr(e.parentElement!,'id');target=e.getAttribute('general')||attr(children(e,'general')[0]||e,'idref');relationType='inheritance';}
      if(!classIds.has(source)||!classIds.has(target)){warnings.push('Se omitió una relación que referencia clases externas o inexistentes.');continue;}
      const connector=documentElements.find(m=>inExtension(m)&&m.localName==='connector'&&(attr(m,'idref')===attr(e,'id')||children(m,'extendedProperties')[0]?.getAttribute('associationclass')===attr(e,'id')));
      if(connector&&['association','aggregation','composition'].includes(relationType)){const nativeSource=children(connector,'source')[0],nativeTarget=children(connector,'target')[0];if(nativeSource&&nativeTarget){source=attr(nativeSource,'idref');target=attr(nativeTarget,'idref');sm=children(nativeSource,'type')[0]?.getAttribute('multiplicity')||sm;tm=children(nativeTarget,'type')[0]?.getAttribute('multiplicity')||tm;}}
      const associationRef=e.getAttributeNS(ARQ,'associationClassId')||children(connector||e,'extendedProperties')[0]?.getAttribute('associationclass');
      const associationClassId=kind(e)==='AssociationClass'?id(e):associationRef?(classIds.get(associationRef)||associationRef):undefined;
      model.relations.push({...(associationClassId?{associationClassId}:{}),id:kind(e)==='AssociationClass'?(e.getAttributeNS(ARQ,'relationId')||`${id(e)}-association`):id(e)||`relation-${model.relations.length}`,sourceId:classIds.get(source)!,targetId:classIds.get(target)!,type:relationType,label:kind(e)==='AssociationClass'?(e.getAttributeNS(ARQ,'relationLabel')??connector?.getAttribute('name')??''):e.getAttribute('name')||'',sourceMultiplicity:e.getAttributeNS(ARQ,'sourceMultiplicity')||sm,targetMultiplicity:e.getAttributeNS(ARQ,'targetMultiplicity')||tm});
    }
    const unsupported=all.filter(e=>e.localName==='packagedElement'&&!['Class','AssociationClass','Package','Association','Dependency','DataType','PrimitiveType'].includes(kind(e)!));
    if(unsupported.length)warnings.push(`${unsupported.length} elementos UML no soportados no se importaron.`);
    if(model.classes.some(c=>c.attributes.some(a=>!a.type)))warnings.push('Hay atributos sin tipo definido; revísalos en el Inspector.');
    if(!documentElements.some(e=>e.hasAttribute('geometry')))warnings.push('El archivo no incluye posiciones de diagrama; se aplicó una distribución inicial.');
    const internalIds=model.classes.flatMap(c=>[c.id,...c.attributes.map(a=>a.id),...c.methods.map(m=>m.id)]).concat(model.relations.map(r=>r.id));
    if(new Set(internalIds).size!==internalIds.length)throw new Error('XMI inválido: identificadores internos duplicados.');
    return{model,warnings};
  }

  private eaColor(style:string):string {
    const value=Number(style.match(/(?:^|;)LCol=(\d+);/)?.[1]??9058846);
    return '#'+[value&255,(value>>>8)&255,(value>>>16)&255].map(v=>v.toString(16).padStart(2,'0')).join('');
  }

  serialize(model:UmlDiagramModel):string {
    const associationByClass=new Map<string,typeof model.relations[number]>();
    for(const relation of model.relations){
      if(!model.classes.some(c=>c.id===relation.sourceId)||!model.classes.some(c=>c.id===relation.targetId))throw new Error('No se puede exportar una relación huérfana.');
      if(!relation.associationClassId)continue;
      if(relation.type!=='association'||!model.classes.some(c=>c.id===relation.associationClassId))throw new Error('La clase de asociación no existe o su relación no es una asociación.');
      if(associationByClass.has(relation.associationClassId))throw new Error('Una clase de asociación no puede pertenecer a dos relaciones diferentes.');
      associationByClass.set(relation.associationClassId,relation);
    }
    const esc=(s:unknown)=>String(s??'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&apos;');
    const guid=(s:string)=>{const words=[2166136261,2246822507,3266489909,668265263];for(const char of s){const code=char.codePointAt(0)!;for(let i=0;i<words.length;i++){words[i]^=code+i*97;words[i]=Math.imul(words[i],16777619+i*2)>>>0;}}const hex=words.map(value=>value.toString(16).padStart(8,'0')).join('').toUpperCase();return`${hex.slice(0,8)}_${hex.slice(8,12)}_${hex.slice(12,16)}_${hex.slice(16,20)}_${hex.slice(20)}`;};
    const xid=(s:string)=>`EAID_${guid(s)}`;
    const modelId=xid(`model:${model.title}`),packageGuid=guid(`package:${model.title}`),packageId=`EAPK_${packageGuid}`,packageElementId=`EAID_${packageGuid}`;
    const parameters=(text:string)=>{const pieces:string[]=[];let depth=0,start=0;for(let i=0;i<text.length;i++){if('<[('.includes(text[i]))depth++;if('>])'.includes(text[i]))depth--;if(text[i]===','&&depth===0){pieces.push(text.slice(start,i));start=i+1;}}if(text.trim())pieces.push(text.slice(start));return pieces.map(piece=>{const match=piece.trim().match(/^(?:(in|out|inout)\s+)?([^:]+):\s*(.+)$/);if(!match)throw new Error('No se puede exportar un parámetro sin formato nombre: tipo. Corrígelo en el Inspector.');return{direction:match[1]||'in',name:match[2].trim(),type:match[3].trim()};});};
    const types=[...new Set(model.classes.flatMap(c=>[...c.attributes.map(a=>a.type),...c.methods.flatMap(m=>[m.returnType,...parameters(m.parameters).map(p=>p.type)])]).filter(t=>!!t&&t!=='void'))];
    const primitiveId=(name:string)=>`EAJava_${name.normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[^a-zA-Z0-9_]/g,'_')}`;
    const typeReference=(name:string)=>{const clazz=model.classes.find(c=>c.name===name);return clazz?xid(clazz.id):primitiveId(name);};
    const associationEnds=(r:typeof model.relations[number],associationId:string)=>{const parts:string[]=[];for(const [side,cid,mult] of [['source',r.sourceId,r.sourceMultiplicity],['target',r.targetId,r.targetMultiplicity]]){const [lower,upper]=mult.includes('..')?mult.split('..'):mult==='*'?['0','*']:[mult,mult];parts.push(`<ownedEnd xmi:id="${xid(r.id)}_${side}" type="${xid(cid)}" association="${associationId}"${side==='source'&&r.type!=='association'?` aggregation="${r.type==='composition'?'composite':'shared'}"`:''}><lowerValue xmi:type="uml:LiteralInteger" xmi:id="${xid(r.id)}_${side}_lower" value="${esc(lower)}"/><upperValue xmi:type="uml:LiteralUnlimitedNatural" xmi:id="${xid(r.id)}_${side}_upper" value="${esc(upper)}"/></ownedEnd>`);}return parts.join('');};
    const p=[`<?xml version="1.0" encoding="UTF-8"?><xmi:XMI xmlns:xmi="${XMI}" xmlns:uml="http://schema.omg.org/spec/UML/2.1" xmlns:arq="${ARQ}" xmi:version="2.1"><xmi:Documentation exporter="Enterprise Architect" exporterVersion="6.5" exporterID="1604"/><uml:Model xmi:id="${modelId}" name="${esc(model.title)}"><packagedElement xmi:type="uml:Package" xmi:id="${packageId}" name="${esc(model.title)}">`];
    for(const c of model.classes){const association=associationByClass.get(c.id);p.push(`<packagedElement xmi:type="uml:${association?'AssociationClass':'Class'}" xmi:id="${xid(c.id)}" arq:internalId="${esc(c.id)}" name="${esc(c.name)}" arq:x="${c.x}" arq:y="${c.y}" arq:description="${esc(c.description)}" arq:stereotype="${esc(c.stereotype)}" arq:color="${esc(c.color)}"${association?` arq:relationId="${esc(association.id)}" arq:relationLabel="${esc(association.label)}" arq:sourceMultiplicity="${esc(association.sourceMultiplicity)}" arq:targetMultiplicity="${esc(association.targetMultiplicity)}" memberEnd="${xid(association.id)}_source ${xid(association.id)}_target"`:""}>`);if(association)p.push(associationEnds(association,xid(c.id)));
      for(const a of c.attributes)p.push(`<ownedAttribute xmi:type="uml:Property" xmi:id="${xid(a.id)}" arq:internalId="${esc(a.id)}" name="${esc(a.name)}" visibility="public" isStatic="false" isReadOnly="false" isDerived="false" isOrdered="false" isUnique="true"${a.type?` type="${typeReference(a.type)}"`:''} arq:isPrimaryKey="${a.isPrimaryKey}"><lowerValue xmi:type="uml:LiteralInteger" xmi:id="${xid(a.id+':lower')}" value="1"/><upperValue xmi:type="uml:LiteralUnlimitedNatural" xmi:id="${xid(a.id+':upper')}" value="1"/></ownedAttribute>`);
      for(const m of c.methods){p.push(`<ownedOperation xmi:type="uml:Operation" xmi:id="${xid(m.id)}" arq:internalId="${esc(m.id)}" name="${esc(m.name)}">`);parameters(m.parameters).forEach((param,n)=>p.push(`<ownedParameter xmi:type="uml:Parameter" xmi:id="${xid(m.id)}_param_${n}" name="${esc(param.name)}" direction="${param.direction}" type="${typeReference(param.type)}"/>`));if(m.returnType&&m.returnType!=='void')p.push(`<ownedParameter xmi:type="uml:Parameter" xmi:id="${xid(m.id)}_return" name="return" direction="return" type="${typeReference(m.returnType)}"/>`);p.push('</ownedOperation>');}
      for(const r of model.relations.filter(r=>r.type==='inheritance'&&r.sourceId===c.id))p.push(`<generalization xmi:type="uml:Generalization" xmi:id="${xid(r.id)}" arq:internalId="${esc(r.id)}" general="${xid(r.targetId)}" name="${esc(r.label)}" arq:sourceMultiplicity="${esc(r.sourceMultiplicity)}" arq:targetMultiplicity="${esc(r.targetMultiplicity)}"/>`);
      p.push('</packagedElement>');}
    for(const r of model.relations){if(!model.classes.some(c=>c.id===r.sourceId)||!model.classes.some(c=>c.id===r.targetId))throw new Error('No se puede exportar una relación huérfana.');if(r.type==='inheritance'||r.associationClassId)continue;
      const common=`xmi:id="${xid(r.id)}" arq:internalId="${esc(r.id)}" name="${esc(r.label)}" arq:sourceMultiplicity="${esc(r.sourceMultiplicity)}" arq:targetMultiplicity="${esc(r.targetMultiplicity)}"${r.associationClassId?` arq:associationClassId="${esc(r.associationClassId)}"`:''}`;
      if(r.type==='dependency'){p.push(`<packagedElement xmi:type="uml:Dependency" ${common} client="${xid(r.sourceId)}" supplier="${xid(r.targetId)}"/>`);continue;}
      p.push(`<packagedElement xmi:type="uml:Association" ${common} memberEnd="${xid(r.id)}_source ${xid(r.id)}_target">`);
      p.push(associationEnds(r,xid(r.id)));
      p.push('</packagedElement>');}
    p.push('</packagedElement></uml:Model>');
    // EA does not create a visible diagram from canonical UML alone. This minimal,
    // tested Sparx extension references the standard UML elements above; the model
    // remains importable by tools that ignore xmi:Extension.
    const diagramId=xid(`diagram:${model.title}`);
    p.push(`<xmi:Extension extender="Enterprise Architect" extenderID="6.5"><elements><element xmi:idref="${packageId}" xmi:type="uml:Package" name="${esc(model.title)}" scope="public"><model package2="${packageElementId}" tpos="0" ea_localid="1" ea_eleType="package"/><properties isSpecification="false" sType="Package" nType="0" scope="public"/><project version="1.0" phase="1.0" complexity="1" status="Proposed"/><code gentype="Java"/><style appearance="BackColor=-1;BorderColor=-1;BorderWidth=-1;FontColor=-1;BorderStyle=0;"/><tags/><xrefs/><extendedProperties tagged="0" package_name="Model"/><packageproperties version="1.0"/><paths/><flags iscontrolled="0" isprotected="0" usedtd="0" logxml="0" packageFlags="isModel=1;VICON=3;"/></element>`);
    model.classes.forEach((c,n)=>p.push(`<element xmi:idref="${xid(c.id)}" xmi:type="uml:Class" name="${esc(c.name)}" scope="public"><model package="${packageId}" tpos="${n}" ea_localid="${n+2}" ea_eleType="element"/><properties isSpecification="false" sType="Class" nType="${model.relations.some(r=>r.associationClassId===c.id)?17:0}" stereotype="${esc(c.stereotype)}" scope="public" isRoot="false" isLeaf="false" isAbstract="false" isActive="false"/><code gentype="Java"/><style appearance="BackColor=-1;BorderColor=-1;BorderWidth=-1;FontColor=-1;BorderStyle=0;"/><tags/><xrefs/><extendedProperties tagged="0" package_name="${esc(model.title)}"${model.relations.some(r=>r.associationClassId===c.id)?` conID="${xid(model.relations.find(r=>r.associationClassId===c.id)!.id)}"`:""}/><attributes>${c.attributes.map((a,i)=>`<attribute xmi:idref="${xid(a.id)}" name="${esc(a.name)}" scope="Public"><properties${a.type?` type="${esc(a.type)}"`:""}/><containment position="${i}"/><coords ordered="0"/></attribute>`).join("")}</attributes><operations>${c.methods.map((m,i)=>`<operation xmi:idref="${xid(m.id)}" name="${esc(m.name)}" scope="Public"><properties position="${i}"/><type type="${esc(m.returnType||'void')}" static="false" const="false" isAbstract="false" returnarray="0"/></operation>`).join("")}</operations></element>`));
    p.push(`</elements><connectors>`);
    model.relations.forEach((r,n)=>{const source=model.classes.find(c=>c.id===r.sourceId)!,target=model.classes.find(c=>c.id===r.targetId)!;p.push(`<connector xmi:idref="${xid(r.id)}" name="${esc(r.label)}"><source xmi:idref="${xid(source.id)}"><model ea_localid="${model.classes.indexOf(source)+2}" type="Class" name="${esc(source.name)}"/><role visibility="Public" targetScope="instance"/><type multiplicity="${esc(r.sourceMultiplicity)}" aggregation="${r.type==='composition'?'composite':r.type==='aggregation'?'shared':'none'}" containment="Unspecified"/></source><target xmi:idref="${xid(target.id)}"><model ea_localid="${model.classes.indexOf(target)+2}" type="Class" name="${esc(target.name)}"/><role visibility="Public" targetScope="instance"/><type multiplicity="${esc(r.targetMultiplicity)}" aggregation="none" containment="Unspecified"/></target><model ea_localid="${n+1}"/><properties ea_type="${r.type==='inheritance'?'Generalization':r.type==='dependency'?'Dependency':r.type==='composition'||r.type==='aggregation'?'Aggregation':'Association'}" direction="Unspecified"${r.associationClassId?' subtype="Class"':''}/><labels lb="${esc(r.sourceMultiplicity)}" mt="${esc(r.label)}" rb="${esc(r.targetMultiplicity)}"/>${r.associationClassId?`<extendedProperties associationclass="${xid(r.associationClassId)}" privatedata1="${model.classes.findIndex(c=>c.id===r.associationClassId)+2}"/>`:""}</connector>`);});
    p.push(`</connectors><primitivetypes><packagedElement xmi:type="uml:Package" xmi:id="EAPrimitiveTypesPackage" name="EA_PrimitiveTypes_Package" visibility="public"><packagedElement xmi:type="uml:Package" xmi:id="EAJavaTypesPackage" name="EA_Java_Types_Package" visibility="public">`);
    types.forEach(t=>p.push(`<packagedElement xmi:type="uml:PrimitiveType" xmi:id="${primitiveId(t)}" name="${esc(t)}" visibility="public"/>`));
    p.push(`</packagedElement></packagedElement></primitivetypes><profiles/><diagrams><diagram xmi:id="${diagramId}"><model package="${packageId}" localID="1" owner="${packageId}"/><properties name="${esc(model.title)}" type="Logical"/><project version="1.0"/><style1 value="ShowPrivate=1;ShowProtected=1;ShowPublic=1;HideRelationships=0;ShowDetails=0;VisibleAttributeDetail=0;ShowOpRetType=1;HideAtts=0;HideOps=0;HideStereo=0;ConnectorNotation=UML 2.1;"/><style2 value="AttPkg=1;ShowOpRetType=1;VisibleAttributeDetail=0;TConnectorNotation=UML 2.1;"/><swimlanes value="locked=false;orientation=0;width=0;inbar=false;names=false;color=-1;"/><matrixitems value="locked=false;matrixactive=false;swimlanesactive=false;width=1;clrLine=0;"/><extendedProperties/><persistentstyle value="DGS=On=0:CNT=8:W=120:H=40:SG=0:SGH=0:AEB=0:;AR=0;DCL=0;"/><xrefs/><elements>`);
    model.classes.forEach((c,n)=>{const left=Math.round(c.x),top=Math.round(c.y),right=left+270,bottom=top+74+Math.max(1,c.attributes.length)*18+Math.max(1,c.methods.length)*18;p.push(`<element geometry="Left=${left};Top=${top};Right=${right};Bottom=${bottom};" subject="${xid(c.id)}" seqno="${n+1}" style="DUID=${xid(c.id)};"/>`);});
    model.relations.forEach(r=>p.push(`<element geometry="SX=0;SY=0;EX=0;EY=0;Path=;" subject="${xid(r.id)}" style=";Hidden=0;"/>`));
    p.push('</elements></diagram></diagrams></xmi:Extension></xmi:XMI>');return p.join('\n');
  }
}
