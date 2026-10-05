import { XMI_GENERAL_MODELS } from "./fixtures/xmi-general-cases.fixture";
import { EA16_VENTAS_ASSOCIATION } from './fixtures/ea16-ventas-association.fixture';
import { UmlXmiService } from './uml-xmi.service';
import { UmlDiagramModel } from './uml-class-diagram.models';
import { EA16_PACIENTE_TURNO } from './fixtures/ea16-paciente-turno.fixture';
describe('CU8 XMI',()=>{
  const service=new UmlXmiService();
  const model:UmlDiagramModel={format:'uml-class-diagram',version:2,title:'Hospital & salud',classes:['Paciente','Turno'].map((name,n)=>({id:`class-${n}`,name,attributes:[{id:`attr-${n}`,name:'Id',type:n?'':'Long',isPrimaryKey:n===0}],methods:[],responsibilities:[],x:70+n*350,y:80,color:'#1e3a8a'})),relations:[{id:'relation-1',sourceId:'class-0',targetId:'class-1',type:'association',label:'reserva',sourceMultiplicity:'1',targetMultiplicity:'0..*'}]};
  it('exporta XML XMI 2.1 y recupera clases, atributos, PK, IDs, posiciones y multiplicidades',()=>{
    const xml=service.serialize(model);expect(xml).toContain('xmi:version="2.1"');
    const loaded=service.parse(xml).model;
    expect(loaded.title).toBe(model.title);
    expect(loaded.classes.map(c=>[c.id,c.name,c.attributes,c.x,c.y])).toEqual(model.classes.map(c=>[c.id,c.name,c.attributes,c.x,c.y]));
    expect(loaded.relations).toEqual(model.relations);
  });
  it('lee variante UML2 con extremos ownedAttribute y referencias memberEnd',()=>{
    const xml=`<xmi:XMI xmlns:xmi="http://schema.omg.org/spec/XMI/2.1" xmlns:uml="http://schema.omg.org/spec/UML/2.1" xmi:version="2.1"><uml:Model xmi:id="m"><packagedElement xmi:type="uml:Class" xmi:id="a" name="Cliente"><ownedAttribute xmi:id="aa" name="correo"><type href="http://example/types#String"/></ownedAttribute><ownedAttribute xmi:id="endA" association="r" type="b"><lowerValue value="0"/><upperValue value="*"/></ownedAttribute></packagedElement><packagedElement xmi:type="uml:Class" xmi:id="b" name="Venta"><ownedAttribute xmi:id="endB" association="r" type="a"/></packagedElement><packagedElement xmi:type="uml:Association" xmi:id="r" memberEnd="endB endA" name="compra"/></uml:Model></xmi:XMI>`;
    const result=service.parse(xml).model;
    expect(result.classes[0].attributes[0].type).toBe('String');
    expect(result.relations[0]).toEqual(expect.objectContaining({sourceId:'a',targetId:'b',sourceMultiplicity:'1',targetMultiplicity:'0..*'}));
  });
  it('rechaza XML inválido y DTD sin resolver recursos externos',()=>{
    expect(()=>service.parse('<invalid>')).toThrow();
    expect(()=>service.parse('<!DOCTYPE x [<!ENTITY secret SYSTEM "file:///etc/passwd">]><x/>')).toThrow();
  });
  it('acepta metadatos EA repetidos, memberEnd XML y multiplicidad -1 sin duplicar relaciones',()=>{
    // Use EA's child references instead of the standard attribute form.
    const ea=service.serialize(model).replace(/ memberEnd="([^"]+)">/,(_,refs:string)=>'>'+refs.split(' ').map(ref=>`<memberEnd xmi:idref="${ref}"/>`).join(''))
      .replace('value="*"','value="-1"')
      .replace('</xmi:XMI>','<xmi:Extension extender="Enterprise Architect"><links><Association xmi:id="id_72_65_6c_61_74_69_6f_6e_2d_31"/></links></xmi:Extension></xmi:XMI>');
    const result=service.parse(ea).model;
    expect(result.classes).toHaveLength(2);
    expect(result.relations).toHaveLength(1);
    expect(result.relations[0].targetMultiplicity).toBe('0..*');
  });
  it('sigue rechazando IDs duplicados en definiciones UML reales',()=>{
    const exported=service.serialize(model),classId=exported.match(/xmi:type="uml:Class" xmi:id="([^"]+)"/)![1];
    const xml=exported.replace('</uml:Model>',`<packagedElement xmi:type="uml:Class" xmi:id="${classId}" name="Duplicada"/></uml:Model>`);
    expect(()=>service.parse(xml)).toThrow('IDs duplicados');
  });
  it('exporta métodos y retorno mediante ownedParameter sin depender de metadatos propios',()=>{
    const source={...model,classes:model.classes.map((c,n)=>({...c,methods:n?[]:[{id:'reservar',name:'reservar',parameters:'turnoId: int',returnType:'Boolean'}]}))};
    const xml=service.serialize(source).replace(/ arq:[^= ]+="[^"]*"/g,'');
    expect(xml).toContain('direction="return"');
    expect(xml).toContain('name="return" direction="return"');
    expect(service.parse(xml).model.classes[0].methods[0]).toEqual(expect.objectContaining({name:'reservar',parameters:'turnoId: int',returnType:'Boolean'}));
  });
  it('reconoce prefijos diferentes para namespaces XMI/UML',()=>{
    const xml=service.serialize(model).replace(/xmlns:xmi=/g,'xmlns:meta=').replace(/xmi:/g,'meta:').replace(/xmlns:uml=/g,'xmlns:UML=').replace(/uml:/g,'UML:');
    expect(service.parse(xml).model.classes).toHaveLength(2);
    expect(service.parse(xml).model.relations).toHaveLength(1);
  });
  it('rechaza XMI 1.x con una causa controlada',()=>{
    expect(()=>service.parse(service.serialize(model).replace('xmi:version="2.1"','xmi:version="1.1"'))).toThrow('Versión no soportada');
  });
  it('importa el fixture REAL exportado por EA16: atributos, método, parámetro, retorno y asociación',()=>{
    const result=service.parse(EA16_PACIENTE_TURNO).model;
    expect(result.classes).toHaveLength(2);
    expect(result.classes.flatMap(c=>c.attributes)).toHaveLength(4);
    const patient=result.classes.find(c=>c.name==='Paciente')!;
    const appointment=result.classes.find(c=>c.name==='Turno')!;
    expect(patient.attributes.map(a=>[a.name,a.type])).toEqual([['id','int'],['nombre','String']]);
    expect(appointment.attributes.map(a=>[a.name,a.type])).toEqual([['fecha','DateTime'],['id','int']]);
    expect(patient.methods).toHaveLength(1);
    expect(patient.methods[0]).toEqual(expect.objectContaining({name:'reservar',parameters:'turnoId: int',returnType:'Boolean'}));
    expect(result.relations).toHaveLength(1);
    const relation=result.relations[0];
    const cardinalities=new Map([[relation.sourceId,relation.sourceMultiplicity],[relation.targetId,relation.targetMultiplicity]]);
    expect(cardinalities.get(patient.id)).toBe('1');
    expect(cardinalities.get(appointment.id)).toBe('0..*');
    expect(relation.label).toBe('reserva');
    expect(result.classes.flatMap(c=>c.attributes).every(a=>!a.isPrimaryKey)).toBe(true);
    const roundTrip=service.parse(service.serialize(result)).model;
    expect(roundTrip.classes.map(c=>c.methods)).toEqual(result.classes.map(c=>c.methods));
    expect(roundTrip.relations).toEqual(result.relations);
  });
  it('conserva identificador explícito UML isID sin inferirlo del nombre',()=>{
    const xml=service.serialize(model).replace(/ arq:isPrimaryKey="[^"]*"/g,'').replace('name="Id"','name="Id" isID="true"');
    const loaded=service.parse(xml).model;
    expect(loaded.classes[0].attributes[0].isPrimaryKey).toBe(true);
    expect(loaded.classes[1].attributes[0].isPrimaryKey).toBe(false);
  });
  it('decodifica XML Windows-1252 de EA sin perder caracteres acentuados',()=>{
    const text='<?xml version="1.0" encoding="windows-1252"?><name>Paciente José</name>';
    const bytes=Uint8Array.from(text,c=>c.charCodeAt(0));
    expect(service.decode(bytes.buffer)).toContain('José');
  });
  it('conserva el extremo de composición aunque EA enumere memberEnd en orden inverso',()=>{
    const source={...model,relations:model.relations.map(r=>({...r,type:'composition' as const}))};
    const xml=service.serialize(source).replace(/memberEnd="([^ ]+) ([^"]+)"/,'memberEnd="$2 $1"');
    const relation=service.parse(xml).model.relations[0];
    expect(relation).toEqual(source.relations[0]);
  });
  it('exporta el modelo Hospital completo con cuatro clases, 17 atributos, tres conectores y diagrama EA',()=>{
    const classes=[
      ['Paciente',['Id:int','nombre:String','Documento:String','Telefono:String']],
      ['Turno',['Id:int','FechaHora:DateTime','Estado:String','motivo:String']],
      ['Profesional',['Id:int','nombre:String','matricula:String','telefono:String','email:String']],
      ['Agenda',['Id:int','Fecha:DateTime','Ubicacion:String','observaciones:String']]
    ].map(([name,attributes],n)=>({id:String(name).toLowerCase(),name:String(name),attributes:(attributes as string[]).map((value,i)=>{const [attribute,type]=value.split(':');return{id:`${n}-${i}`,name:attribute,type,isPrimaryKey:false};}),methods:n?[]:[{id:'reservar',name:'reservar',parameters:'turnoId: int',returnType:'Boolean'}],responsibilities:[],x:60+n%2*340,y:60+Math.floor(n/2)*290,color:'#1e3a8a'}));
    const hospital:UmlDiagramModel={format:'uml-class-diagram',version:2,title:'Hospital',classes,relations:[
      {id:'reserva',sourceId:'paciente',targetId:'turno',type:'association',label:'reserva',sourceMultiplicity:'1',targetMultiplicity:'0..*'},
      {id:'atiende',sourceId:'turno',targetId:'profesional',type:'association',label:'atendido por',sourceMultiplicity:'*',targetMultiplicity:'1'},
      {id:'posee',sourceId:'profesional',targetId:'agenda',type:'composition',label:'posee',sourceMultiplicity:'1',targetMultiplicity:'1..*'}
    ]};
    const xml=service.serialize(hospital),doc=new DOMParser().parseFromString(xml,'application/xml');
    expect(xml).toContain('exporter="Enterprise Architect"');
    expect(xml).toContain('<diagrams>');
    expect(xml).toContain('HideAtts=0');
    expect(xml).toContain('HideOps=0');
    const ids=Array.from(doc.getElementsByTagName('*')).flatMap(e=>Array.from(e.attributes).filter(a=>a.localName==='id'&&/XMI/i.test(a.namespaceURI||'')).map(a=>a.value));
    expect(new Set(ids).size).toBe(ids.length);
    const loaded=service.parse(xml).model;
    expect(loaded.classes.map(c=>c.name).sort()).toEqual(['Agenda','Paciente','Profesional','Turno']);
    expect(loaded.classes.flatMap(c=>c.attributes)).toHaveLength(17);
    expect(loaded.relations.map(r=>[r.label,r.sourceMultiplicity,r.targetMultiplicity])).toEqual([
      ['reserva','1','0..*'],['atendido por','*','1'],['posee','1','1..*']
    ]);
  });
  it('importa el diagrama real de EA16 con posiciones y clase de asociación',()=>{
    const loaded=service.parse(EA16_VENTAS_ASSOCIATION).model;
    expect(loaded.title).toBe('ventasssMARTES');
    expect(loaded.classes).toHaveLength(5);
    expect(loaded.classes.flatMap(c=>c.attributes)).toHaveLength(13);
    expect(loaded.classes.find(c=>c.name==='Cliente')).toEqual(expect.objectContaining({x:374,y:562}));
    const detail=loaded.classes.find(c=>c.name==='detalle')!;
    const relation=loaded.relations.find(r=>r.associationClassId===detail.id)!;
    expect(loaded.classes.find(c=>c.id===relation.sourceId)?.name).toBe('Venta');
    expect(loaded.classes.find(c=>c.id===relation.targetId)?.name).toBe('Producto');
    expect(relation).toEqual(expect.objectContaining({sourceMultiplicity:'*',targetMultiplicity:'1..*',label:''}));
  });
  it('exporta posiciones exactas, tamaño del editor, colores y enlace nativo de clase de asociación',()=>{
    const detail={...model.classes[0],id:'detail',name:'Detalle',attributes:[],x:0,y:10,color:'#7c3aed'};
    const source={...model,classes:[...model.classes,detail],relations:model.relations.map(r=>({...r,associationClassId:detail.id}))};
    const xml=service.serialize(source);
    expect(xml).toContain('Left=0;Top=10;Right=270;Bottom=120;');
    expect(xml).toContain('associationclass=');
    expect(xml).not.toMatch(/(?:BCol|LCol|LWth|FCol|Grad)=/);
    const loaded=service.parse(xml).model;
    expect(loaded.relations).toEqual(source.relations);
    expect(loaded.classes.map(c=>[c.x,c.y,c.color])).toEqual(source.classes.map(c=>[c.x,c.y,c.color]));
    const nativeOnly=service.parse(xml.replace(/ arq:[^= ]+="[^"]*"/g,'')).model;
    expect(nativeOnly.classes.find(c=>c.name==='Detalle')).toEqual(expect.objectContaining({x:0,y:10,color:'#1e3a8a'}));
    expect(nativeOnly.relations[0].associationClassId).toBe(nativeOnly.classes.find(c=>c.name==='Detalle')!.id);
  });
  it('no crea referencias huérfanas',()=>{
    const xml=service.serialize(model).replace(/(<ownedEnd[^>]* type=")[^"]+/, '$1missing');
    const preview=service.parse(xml);expect(preview.model.relations).toHaveLength(0);expect(preview.warnings.length).toBeGreaterThan(1);
  });
  for(const source of XMI_GENERAL_MODELS){
    it('exporta clases de asociación UML estándar: '+source.title,()=>{
      const xml=service.serialize(source),doc=new DOMParser().parseFromString(xml,'application/xml');
      const canonical=Array.from(doc.getElementsByTagName('packagedElement')).filter(e=>e.getAttribute('xmi:type')==='uml:AssociationClass');
      expect(canonical).toHaveLength(source.relations.filter(r=>r.associationClassId).length);
      expect(canonical.every(c=>c.getElementsByTagName('ownedEnd').length===2)).toBe(true);
      const loaded=service.parse(xml).model;
      expect(loaded.classes.map(c=>[c.id,c.name,c.attributes,c.methods,c.x,c.y,c.color])).toEqual(source.classes.map(c=>[c.id,c.name,c.attributes,c.methods,c.x,c.y,c.color]));
      expect([...loaded.relations].sort((a,b)=>a.id.localeCompare(b.id))).toEqual([...source.relations].sort((a,b)=>a.id.localeCompare(b.id)));
      const native=service.parse(xml.replace(/ arq:[^= ]+="[^"]*"/g,'')).model;
      expect(native.relations.filter(r=>r.associationClassId)).toHaveLength(canonical.length);
      expect(native.relations).toHaveLength(source.relations.length);
    });
  }
  it('rechaza enlaces de clase de asociación inexistentes o reutilizados sin perderlos silenciosamente',()=>{
    const source=XMI_GENERAL_MODELS[0];
    expect(()=>service.serialize({...source,relations:source.relations.map((r,i)=>i===0?{...r,associationClassId:'missing'}:r)})).toThrow('no existe');
    expect(()=>service.serialize({...source,relations:source.relations.map((r,i)=>i===1?{...r,associationClassId:source.relations[0].associationClassId}:r)})).toThrow('dos relaciones');
  });

});
