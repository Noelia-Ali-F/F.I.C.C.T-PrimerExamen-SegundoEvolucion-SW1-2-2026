package com.arqnova.servicios.impl;

import com.arqnova.modelo.ia.AiConversation.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Snapshot-only review evidence. Does not mutate UML or trust provider severity as proof. */
public final class UmlReviewEvidence {
    private final ConsultativeEvidence references=new ConsultativeEvidence();
    private static final Pattern TARGET=Pattern.compile("\\b(clase|atributo|metodo|relacion)\\s+[\"«']?([\\p{L}][\\p{L}\\p{N}_-]*)");
    private static final Set<String> GENERIC=Set.of("actual","solicitada","solicitado","con","sin","entre","de","del","que","es","no","duplicado","duplicada","inexistente","asociacion");
    public Optional<Answer> local(Turn turn,Context context){
        if(rows(context.diagram().get("classes")).isEmpty())return Optional.of(new Answer(
                "El UML está vacío (versión "+context.diagramVersion()+"). No hay clases que revisar ni hallazgos de diseño que demostrar. No se modificó el lienzo.",List.of(),"LOCAL","empty-review"));
        var missing=missingTarget(turn.instruction(),context);
        return missing.map(name->new Answer("El elemento "+name+" no consta en el snapshot UML (versión "+context.diagramVersion()+"). Indica el nombre o ID correcto; no se generaron hallazgos ni cambios.",List.of(),"LOCAL","review-target-clarification"));
    }
    public Answer review(Turn turn,Context context,List<Finding> proposed,String provider,String model){
        List<Finding> verified=verified(context);var findings=new ArrayList<>(verified.stream().limit(12).toList());
        for(var finding:proposed){
            if(findings.size()>=12)break;
            Finding checked=check(turn,context,finding,verified);
            if(findings.stream().noneMatch(f->Objects.equals(f.elementId(),checked.elementId())&&f.message().equals(checked.message())))findings.add(checked);
        }
        String summary=summary(turn,context);
        return new Answer(summary,List.copyOf(findings),provider,model);
    }
    public List<Finding> verified(Context context){
        var result=new ArrayList<Finding>();var classes=rows(context.diagram().get("classes"));
        duplicates(classes,"clase",null,result);
        Set<String> ids=new HashSet<>();classes.forEach(c->ids.add(text(c.get("id"))));
        for(var c:classes){
            duplicates(rows(c.get("attributes")),"atributo",text(c.get("name")),result);
            duplicates(rows(c.get("methods")),"método",text(c.get("name")),result);
            for(var method:rows(c.get("methods"))){
                var names=parameters(method).stream().map(this::parameterName).filter(s->!s.isBlank()).toList();
                if(new HashSet<>(names).size()<names.size())result.add(new Finding("ERROR",text(method.get("id")),"Parámetros con nombre duplicado en "+text(c.get("name"))+"."+text(method.get("name"))+": "+String.join(", ",names)+"."));
            }
        }
        Set<List<String>> signatures=new HashSet<>();
        for(var relation:rows(context.diagram().get("relations"))){
            String id=text(relation.get("id")),source=text(relation.get("sourceId")),target=text(relation.get("targetId"));
            if(!ids.contains(source)||!ids.contains(target)){result.add(new Finding("ERROR",id,"La relación "+id+" referencia un extremo inexistente: origen="+source+", destino="+target+"."));continue;}
            String sm=text(relation.get("sourceMultiplicity")),tm=text(relation.get("targetMultiplicity"));
            String a=source,b=target,x=sm,y=tm;
            if("association".equals(relation.get("type"))&&a.compareTo(b)>0){a=target;b=source;x=tm;y=sm;}
            if(!signatures.add(List.of(text(relation.get("type")),a,b,text(relation.get("label")).strip(),x,y)))result.add(new Finding("ERROR",id,"Relación exactamente duplicada: "+source+"–"+target+", tipo "+text(relation.get("type"))+", multiplicidades "+sm+" / "+tm+"."));
            if(!Set.of("inheritance","dependency").contains(text(relation.get("type")))&&(sm.isBlank()||tm.isBlank()))result.add(new Finding("WARNING",id,
                    "Multiplicidad no especificada en "+(sm.isBlank()?"origen "+className(context,source):"")+(sm.isBlank()&&tm.isBlank()?" y ":"")+(tm.isBlank()?"destino "+className(context,target):"")+". Es información desconocida, no una cardinalidad inferida. "+ends(context,relation)));
        }
        return result;
    }
    private void duplicates(List<Map<String,Object>> members,String kind,String owner,List<Finding> result){
        var seen=new HashSet<String>();
        for(var member:members){
            String key=normal(text(member.get("name")));
            if(kind.equals("método"))key+="("+String.join(",",parameters(member).stream().map(this::parameterType).toList())+")";
            if(!seen.add(key))result.add(new Finding("ERROR",text(member.get("id")),"Nombre/firma duplicado de "+kind+" «"+text(member.get("name"))+"»"+(owner==null?"":" en la clase "+owner)+"; aparece más de una vez en el snapshot."));
        }
    }
    private Finding check(Turn turn,Context context,Finding raw,List<Finding> verified){
        String message=raw.message(),value=normal(message),id=raw.elementId();
        boolean advisory=value.matches("(?s).*\\b(?:podrias|podriamos|puedes|seria|recomienda|recomiendo|recomendacion|recomendable|considera|considerar|conviene|convendria|sugiere|sugiero|sugerencia|mejorar|mejora|evaluar|verificar|revisar|preferible|alternativa)\\b.*");
        var index=index(context);var element=id==null?null:index.get(id);
        if(id!=null&&element==null)return new Finding("INFO",null,"No consta el elemento con ID "+clip(id,128)+". La observación del proveedor no se confirmó; solicita el ID correcto.");
        if(value.matches("(?s).*\\b(?:propone|propongo|acordaron|decidieron|consenso|propuesta de)\\b.*")||value.matches("(?s).*\\b(?!(?:se|te|le|les|que|es|lo|la|el|no|ia|asistente)\\b)[\\p{L}]+\\s+(?:sugiere|recomienda|prefiere|eligio|apoyo|decidio|acordo)\\b.*"))return new Finding("INFO",null,
                "Propuestas y decisiones individuales: consulte las declaraciones literales del resumen con sus autores. No prueban aplicación al UML ni consenso del equipo.");
        var missing=missingTarget(message,context);
        if(missing.isPresent())return new Finding("INFO",null,"No consta el elemento "+missing.get()+". "+(advisory?"Recomendación hipotética, no estructura actual: "+clip(references.validateReferences(turn,context,message),1100):"No se confirma el problema indicado por la IA."));
        // Bind name-only observations only when they identify exactly one snapshot element.
        if(element==null){
            var named=referenced(message,context);
            if(named.size()==1){id=text(named.get(0).get("id"));element=named.get(0);}
        }
        if(value.contains("duplicad")&&id!=null){String finalId=id;var actual=verified.stream().filter(f->"ERROR".equals(f.severity())&&Objects.equals(finalId,f.elementId())).findFirst();if(actual.isPresent())return actual.get();}
        var relation=element!=null&&element.containsKey("sourceId")?element:findRelation(message,context);
        if(relation!=null&&(value.contains("multiplic")||value.contains("cardinal")||value.contains("extremo")||value.contains("por cada")))return new Finding("INFO",text(relation.get("id")),
                "Recomendación de cardinalidad pendiente de confirmar con el dominio. "+ends(context,relation)+" Un nuevo valor requiere especificar cuántos elementos se cuentan por cada instancia del otro extremo; no se deduce del valor opuesto ni se aplica desde Revisión.");
        String facts=element==null?"":counts(context,element);
        boolean countClaim=value.matches(".*\\b(?:parametros?|atributos?|metodos?|relaciones?)\\b.*")&&value.matches(".*\\b(?:\\d+|cero|un|uno|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\\b.*");
        if(countClaim)return new Finding("INFO",id,"Comprobación de cantidades del snapshot: "+(facts.isBlank()?totals(context):facts)+" La cantidad o el problema sugerido por la IA no se acepta como error sin evidencia específica.");
        String safe=references.validateReferences(turn,context,message);
        if(!safe.equals(message.strip()))return new Finding("INFO",id,clip(safe,1400));
        if("ERROR".equals(raw.severity()))return new Finding("INFO",id,"Criterio de diseño no demostrado como ERROR; necesita requisitos o evidencia adicional. "+facts);
        if(!advisory)return new Finding("INFO",id,"Observación no demostrada por el snapshot; requiere aclaración, no se acepta como hecho ni error confirmado. "+facts);
        return new Finding("INFO",id,"Recomendación/opinión de diseño, no error UML confirmado: "+clip(safe,1100)+(facts.isBlank()?"":"\nReferencia verificable: "+facts));
    }
    private String summary(Turn turn,Context context){
        var out=new StringBuilder("Revisión del snapshot UML · versión "+context.diagramVersion()+". No se modificó el lienzo.\n"+totals(context)+"\nERROR identifica un defecto calculado sobre este snapshot; WARNING indica información incompleta; INFO distingue recomendaciones o afirmaciones pendientes de comprobar. Se muestran hasta 12 hallazgos y un resumen acotado de elementos y declaraciones.\n");
        var targets=referenced(turn.instruction(),context);
        if(targets.isEmpty())targets=rows(context.diagram().get("classes")).stream().limit(4).toList();
        for(var target:targets.stream().limit(6).toList())out.append(counts(context,target)).append('\n');
        var history=new LinkedHashMap<String,Turn>();history.put(turn.requestId(),turn);
        for(var list:List.of(context.groupHistory(),context.userHistory(),context.relevantHistory()))for(var item:list)if(context.projectId().equals(item.projectId()))history.putIfAbsent(item.requestId(),item);
        int proposals=0;
        for(var item:history.values()){
            String instruction=normal(item.instruction()).stripLeading();
            if(!(instruction.startsWith("propuesta:")||instruction.startsWith("propongo ")||instruction.startsWith("decision:")))continue;
            if(proposals++==4)break;
            out.append(instruction.startsWith("decision:")?"Decisión declarada individual":"Propuesta individual").append(" literal de ").append(clip(item.authorName(),120)).append(" (ID ").append(clip(item.authorId(),128)).append("): «").append(clip(item.instruction(),450)).append(item.instruction().length()>450?"…":"").append("». No acredita aplicación ni consenso.\n");
        }
        return out.toString().strip();
    }
    private String counts(Context context,Map<String,Object> element){
        String id=text(element.get("id")),name=text(element.get("name"));
        if(element.containsKey("sourceId"))return ends(context,element);
        if(rows(context.diagram().get("classes")).stream().anyMatch(c->id.equals(c.get("id")))){
            long relations=rows(context.diagram().get("relations")).stream().filter(r->id.equals(r.get("sourceId"))||id.equals(r.get("targetId"))).count();
            return "Clase "+name+" ("+id+"): "+rows(element.get("attributes")).size()+" atributos, "+rows(element.get("methods")).size()+" métodos y "+relations+" relaciones incidentes.";
        }
        if(element.containsKey("parameters")||element.containsKey("returnType"))return "Método "+name+" ("+id+"): "+parameters(element).size()+" parámetros ["+clip(String.join(", ",parameters(element)),200)+"], retorno "+text(element.get("returnType"))+".";
        return "Atributo "+name+" ("+id+"), tipo "+text(element.get("type"))+".";
    }
    private String totals(Context context){var classes=rows(context.diagram().get("classes"));return "Inventario verificable: "+classes.size()+" clases, "+classes.stream().mapToInt(c->rows(c.get("attributes")).size()).sum()+" atributos, "+classes.stream().mapToInt(c->rows(c.get("methods")).size()).sum()+" métodos y "+rows(context.diagram().get("relations")).size()+" relaciones.";}
    private String ends(Context context,Map<String,Object> relation){
        String source=className(context,text(relation.get("sourceId"))),target=className(context,text(relation.get("targetId")));
        return "Relación "+text(relation.get("id"))+": extremo origen "+source+" [sourceMultiplicity="+bound(relation.get("sourceMultiplicity"))+"] cuenta "+source+" por cada "+target+"; extremo destino "+target+" [targetMultiplicity="+bound(relation.get("targetMultiplicity"))+"] cuenta "+target+" por cada "+source+". Para cambiar cuántos "+source+" hay por "+target+", modifica el extremo "+source+"; para cambiar cuántos "+target+" hay por "+source+", el extremo "+target+".";
    }
    private String bound(Object value){String s=text(value);return s.isBlank()?"no especificada":s;}
    private String className(Context c,String id){return rows(c.diagram().get("classes")).stream().filter(v->id.equals(v.get("id"))).map(v->text(v.get("name"))).findFirst().orElse(id);}
    private Optional<String> missingTarget(String instruction,Context context){
        var matcher=TARGET.matcher(normal(instruction));
        while(matcher.find()){
            String kind=matcher.group(1),name=matcher.group(2);if(GENERIC.contains(name))continue;
            if(kind.equals("relacion")&&findRelation(instruction,context)!=null)continue;
            boolean found=elements(context,kind).stream().anyMatch(e->normal(text(e.get("id"))).equals(name)||normal(text(e.get("name"))).equals(name));
            if(!found)return Optional.of(name);
        }
        return Optional.empty();
    }
    private List<Map<String,Object>> elements(Context c,String kind){if(kind.equals("clase"))return rows(c.diagram().get("classes"));if(kind.equals("relacion"))return rows(c.diagram().get("relations"));return rows(c.diagram().get("classes")).stream().flatMap(v->rows(v.get(kind.equals("atributo")?"attributes":"methods")).stream()).toList();}
    private List<Map<String,Object>> referenced(String text,Context context){var matcher=TARGET.matcher(normal(text));var result=new LinkedHashMap<String,Map<String,Object>>();while(matcher.find()){String name=matcher.group(2);for(var element:index(context).values())if(normal(text(element.get("name"))).equals(name)||normal(text(element.get("id"))).equals(name))result.put(text(element.get("id")),element);}return new ArrayList<>(result.values());}
    private Map<String,Object> findRelation(String message,Context context){String value=normal(message);var matches=rows(context.diagram().get("relations")).stream().filter(r->mentions(value,text(r.get("id")))||(mentions(value,className(context,text(r.get("sourceId"))))&&mentions(value,className(context,text(r.get("targetId")))))).toList();return matches.size()==1?matches.get(0):null;}
    private boolean mentions(String value,String name){return !name.isBlank()&&Pattern.compile("(?<![\\p{L}\\p{N}_])"+Pattern.quote(normal(name))+"(?![\\p{L}\\p{N}_])").matcher(value).find();}
    private Map<String,Map<String,Object>> index(Context context){var index=new LinkedHashMap<String,Map<String,Object>>();for(var c:rows(context.diagram().get("classes"))){index.put(text(c.get("id")),c);for(var key:List.of("attributes","methods"))for(var member:rows(c.get(key)))index.put(text(member.get("id")),member);}for(var r:rows(context.diagram().get("relations")))index.put(text(r.get("id")),r);return index;}
    private List<String> parameters(Map<String,Object> method){
        Object value=method.get("parameters");if(value instanceof List<?> list)return list.stream().map(String::valueOf).toList();
        String source=text(value);if(source.isBlank())return List.of();var parts=new ArrayList<String>();int depth=0,start=0;
        for(int i=0;i<source.length();i++){char c=source.charAt(i);if(c=='<'||c=='('||c=='[')depth++;if(c=='>'||c==')'||c==']')depth--;if(c==','&&depth==0){parts.add(source.substring(start,i).strip());start=i+1;}}parts.add(source.substring(start).strip());return parts;
    }
    private String parameterName(String value){String v=value.strip();if(v.contains(":"))return normal(v.split(":",2)[0].strip());String[] words=v.split("\\s+");return words.length>1?normal(words[words.length-1]):"";}
    private String parameterType(String value){String v=value.strip();if(v.contains(":"))return normal(v.split(":",2)[1].strip());return normal(v.replaceFirst("\\s+[\\p{L}_][\\p{L}\\p{N}_]*$", ""));}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> rows(Object value){return value instanceof List<?> list?list.stream().filter(Map.class::isInstance).map(v->(Map<String,Object>)v).toList():List.of();}
    private static String text(Object value){return value==null?"":String.valueOf(value);}
    private static String normal(String value){return Normalizer.normalize(value.replaceAll("[*`«»]|(?<!\\w)_{1,3}|_{1,3}(?!\\w)",""),Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);}
    private static String clip(String value,int size){return value==null?"":value.substring(0,Math.min(size,value.length()));}
}
