package com.arqnova.servicios.impl;

import com.arqnova.modelo.dto.response.UmlAiOperationResponse;
import java.text.Normalizer;
import java.util.*;

/** Pure planning: validates a complete batch on a deep copy before any persistence occurs. */
public final class UmlMutationPlanner {
    public record Element(String type, String id) {}
    public record Plan(Map<String,Object> model, Set<Element> affected) {}
    private static final Set<String> RELATIONS=Set.of("association","aggregation","composition","inheritance","dependency");

    public Plan plan(Map<String,Object> original,List<UmlAiOperationResponse> operations,String requestId) {
        if(original==null||operations==null||operations.isEmpty()||operations.size()>50)
            throw new IllegalArgumentException("Indica entre 1 y 50 operaciones UML");
        if(requestId==null||!requestId.matches("[A-Za-z0-9_-]{8,100}"))throw new IllegalArgumentException("Solicitud inválida");
        Map<String,Object> model=copyMap(original);
        List<Map<String,Object>> classes=rows(model,"classes"),relations=rows(model,"relations");
        Set<Element> affected=new LinkedHashSet<>();
        int index=0;
        for(var operation:operations) {
            if(operation==null||operation.getType()==null)throw new IllegalArgumentException("Operación UML inválida");
            Map<String,Object> p=operation.getParameters()==null?Map.of():operation.getParameters();
            String target=operation.getTargetId();
            String generated="ai-"+requestId+"-"+(index++);
            switch(operation.getType()) {
                case "CLASS_CREATE" -> {
                    var node=newClass(id(p.get("id"),id(target,generated)),text(p.get("name")),classes.size());
                    node.put("attributes",members(p.get("attributes"),false,generated));
                    node.put("methods",members(p.get("methods"),true,generated));
                    unique(classes,node,null);classes.add(node);affected.add(new Element("class",(String)node.get("id")));
                }
                case "CLASS_UPDATE" -> {
                    var node=find(classes,target);patch(node,p,Set.of("name","description","stereotype"));
                    unique(classes,node,target);affected.add(new Element("class",target));
                }
                case "CLASS_DELETE" -> {
                    find(classes,target);Set<String> removed=new HashSet<>();removed.add(target);
                    for(var relation:relations)if(endpoint(relation,target)) {
                        affected.add(new Element("relation",text(relation.get("id"))));
                        if(relation.get("associationClassId") instanceof String a)removed.add(a);
                    }
                    classes.removeIf(c->removed.contains(c.get("id")));
                    relations.removeIf(r->removed.contains(r.get("sourceId"))||removed.contains(r.get("targetId")));
                    relations.forEach(r->{if(removed.contains(r.get("associationClassId")))r.remove("associationClassId");});
                    removed.forEach(v->affected.add(new Element("class",v)));
                }
                case "ATTRIBUTE_CREATE","ATTRIBUTE_UPDATE","ATTRIBUTE_DELETE","METHOD_CREATE","METHOD_UPDATE","METHOD_DELETE" -> {
                    boolean method=operation.getType().startsWith("METHOD_");String classId=text(p.get("classId"));
                    var node=find(classes,classId);var list=rows(node,method?"methods":"attributes");
                    String memberId=operation.getType().endsWith("_CREATE")?id(target,id(p.get("id"),generated)):target;
                    if(operation.getType().endsWith("_CREATE")) {
                        var member=new LinkedHashMap<String,Object>();member.put("id",memberId);member.put("name",text(p.get("name")));
                        if(method){member.put("parameters",string(p.getOrDefault("parameters","")));member.put("returnType",text(p.getOrDefault("returnType","void")));}
                        else {member.put("type",text(p.getOrDefault("type","String")));member.put("isPrimaryKey",Boolean.TRUE.equals(p.get("isPrimaryKey")));}
                        unique(list,member,null);list.add(member);
                    } else {
                        var member=find(list,memberId);
                        if(operation.getType().endsWith("_DELETE"))list.remove(member);
                        else {patch(member,p,method?Set.of("name","parameters","returnType"):Set.of("name","type","isPrimaryKey"));unique(list,member,memberId);}
                    }
                    // Class locks also protect nested attributes and methods.
                    affected.add(new Element("class",classId));affected.add(new Element(method?"class":"attribute",method?classId:memberId));
                }
                case "RELATION_CREATE","RELATION_UPDATE" -> {
                    boolean create=operation.getType().endsWith("CREATE");
                    var relation=create?new LinkedHashMap<String,Object>():find(relations,target);
                    String relationId=create?id(target,id(p.get("id"),generated)):target;
                    if(create){relation.put("id",relationId);relation.put("type","association");relation.put("label","");relation.put("sourceMultiplicity","1");relation.put("targetMultiplicity","0..*");}
                    patch(relation,p,Set.of("sourceId","targetId","type","label","sourceMultiplicity","targetMultiplicity"));
                    find(classes,text(relation.get("sourceId")));find(classes,text(relation.get("targetId")));
                    validateRelation(relation);
                    if(create){if(relations.stream().anyMatch(r->relationId.equals(r.get("id"))))throw invalid("Relación duplicada");relations.add(relation);}
                    affected.add(new Element("relation",relationId));
                    affected.add(new Element("class",text(relation.get("sourceId"))));affected.add(new Element("class",text(relation.get("targetId"))));
                    associationClass(relation,classes,string(p.getOrDefault("associationClassName","")),generated,affected);
                }
                case "RELATION_DELETE" -> {
                    var relation=find(relations,target);relations.remove(relation);affected.add(new Element("relation",target));
                    if(relation.get("associationClassId") instanceof String a){classes.removeIf(c->a.equals(c.get("id")));affected.add(new Element("class",a));}
                }
                default -> throw invalid("Operación no soportada: "+operation.getType());
            }
        }
        validateGraph(classes,relations);
        return new Plan(model,Set.copyOf(affected));
    }

    private void associationClass(Map<String,Object> r,List<Map<String,Object>> classes,String requested,String prefix,Set<Element> affected) {
        if(!"association".equals(r.get("type"))||!text(r.get("sourceMultiplicity")).endsWith("*")||!text(r.get("targetMultiplicity")).endsWith("*"))return;
        if(r.get("associationClassId") instanceof String existing){find(classes,existing);return;}
        var source=find(classes,text(r.get("sourceId")));var target=find(classes,text(r.get("targetId")));
        if(!requested.isBlank()) {
            var explicit=classes.stream().filter(c->normal(text(c.get("name"))).equals(normal(requested))).findFirst();
            if(explicit.isPresent()){
                var node=explicit.get();String classId=text(node.get("id"));
                if(node==source||node==target)throw invalid("La clase de asociación debe ser diferente de sus extremos");
                node.put("stereotype","Clase de asociación");r.put("associationClassId",classId);affected.add(new Element("class",classId));return;
            }
        }
        String base=requested.isBlank()?"Detalle"+source.get("name")+target.get("name"):requested.trim();
        String name=base;int suffix=2;
        while(hasName(classes,name))name=base+(suffix++);
        String classId=prefix+"-assoc";var node=newClass(classId,name,classes.size());
        node.put("description","Atributos de la asociación");node.put("stereotype","Clase de asociación");
        node.put("color","#2563eb");
        node.put("x",Math.max(20,(coordinate(source,"x")+coordinate(target,"x"))/2));
        node.put("y",Math.max(20,(coordinate(source,"y")+coordinate(target,"y"))/2+180));
        unique(classes,node,null);classes.add(node);r.put("associationClassId",classId);affected.add(new Element("class",classId));
    }
    private Map<String,Object> newClass(String id,String name,int count) {
        var node=new LinkedHashMap<String,Object>();node.put("id",id);node.put("name",name);node.put("description","");node.put("stereotype","Entidad");
        node.put("attributes",new ArrayList<>());node.put("methods",new ArrayList<>());node.put("responsibilities",new ArrayList<>());
        node.put("x",70+(count%4)*350);node.put("y",70+(count/4)*300);node.put("color",List.of("#1e3a8a","#2563eb","#7c3aed","#b45309").get(count%4));return node;
    }
    private List<Map<String,Object>> members(Object value,boolean method,String prefix) {
        if(value==null)return new ArrayList<>();if(!(value instanceof List<?> list)||list.size()>100)throw invalid("Miembros inválidos");
        var result=new ArrayList<Map<String,Object>>();int n=0;
        for(Object item:list) {
            if(!(item instanceof Map<?,?> raw))throw invalid("Miembro inválido");
            var member=new LinkedHashMap<String,Object>();member.put("id",id(raw.get("id"),prefix+(method?"-method-":"-attr-")+(n++)));member.put("name",text(raw.get("name")));
            if(method){member.put("parameters",string(raw.get("parameters")));member.put("returnType",raw.get("returnType")==null?"void":text(raw.get("returnType")));}
            else {member.put("type",raw.get("type")==null?"String":text(raw.get("type")));member.put("isPrimaryKey",Boolean.TRUE.equals(raw.get("isPrimaryKey")));}
            unique(result,member,null);result.add(member);
        }return result;
    }
    private void validateGraph(List<Map<String,Object>> classes,List<Map<String,Object>> relations) {
        if(classes.size()>500||relations.size()>2000)throw invalid("El diagrama supera el tamaño admitido");
        Set<String> ids=new HashSet<>();Set<String> names=new HashSet<>();
        for(var node:classes){if(!ids.add(text(node.get("id")))||!names.add(normal(text(node.get("name")))))throw invalid("Clases duplicadas");}
        Set<String> relationIds=new HashSet<>(),associationIds=new HashSet<>();
        for(var relation:relations) {
            validateRelation(relation);
            if(!relationIds.add(text(relation.get("id")))||!ids.contains(text(relation.get("sourceId")))||!ids.contains(text(relation.get("targetId"))))throw invalid("Relación inválida");
            if(relation.get("associationClassId") instanceof String a&&(!ids.contains(a)||!associationIds.add(a)||a.equals(relation.get("sourceId"))||a.equals(relation.get("targetId"))))throw invalid("Clase de asociación inválida");
        }
    }
    private void validateRelation(Map<String,Object> r) {
        if(!RELATIONS.contains(text(r.get("type"))))throw invalid("Tipo de relación inválido");
        multiplicity(text(r.get("sourceMultiplicity")));multiplicity(text(r.get("targetMultiplicity")));
    }
    private void multiplicity(String value) {
        if(!value.matches("\\*|\\d{1,9}|\\d{1,9}\\.\\.(?:\\d{1,9}|\\*)"))throw invalid("Multiplicidad inválida");
        if(value.contains("..")&&!value.endsWith("*")){String[] bounds=value.split("\\.\\.");if(Long.parseLong(bounds[0])>Long.parseLong(bounds[1]))throw invalid("Multiplicidad invertida");}
    }
    private void patch(Map<String,Object> target,Map<String,Object> parameters,Set<String> allowed) {
        for(var key:allowed)if(parameters.containsKey(key)) {
            Object v=parameters.get(key);
            if("isPrimaryKey".equals(key)){if(!(v instanceof Boolean))throw invalid("Indicador de clave inválido");target.put(key,v);}
            else target.put(key,Set.of("description","label","parameters").contains(key)?string(v):text(v));
        }
    }
    private void unique(List<Map<String,Object>> rows,Map<String,Object> value,String excludedId) {
        for(var row:rows)if(excludedId==null||!excludedId.equals(row.get("id")))
            if(Objects.equals(row.get("id"),value.get("id"))||normal(text(row.get("name"))).equals(normal(text(value.get("name")))))throw invalid("Identificador o nombre duplicado");
    }
    private boolean hasName(List<Map<String,Object>> classes,String name){return classes.stream().anyMatch(c->normal(text(c.get("name"))).equals(normal(name)));}
    private String normal(String s){return Normalizer.normalize(s.trim(),Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);}
    private boolean endpoint(Map<String,Object> r,String id){return id.equals(r.get("sourceId"))||id.equals(r.get("targetId"));}
    private double coordinate(Map<String,Object> node,String key){return node.get(key) instanceof Number n&&Double.isFinite(n.doubleValue())?n.doubleValue():70;}
    private String id(Object v,String fallback){return v==null?fallback:text(v);}
    private String text(Object v){String s=string(v).trim();if(s.isBlank())throw invalid("Falta un nombre o identificador UML");return s;}
    private String string(Object v){if(v==null)return "";if(!(v instanceof String s)||s.length()>4000)throw invalid("Texto UML inválido");return s;}
    private Map<String,Object> find(List<Map<String,Object>> rows,String id){return rows.stream().filter(v->Objects.equals(v.get("id"),id)&&id!=null).findFirst().orElseThrow(()->invalid("Elemento UML inexistente: "+id));}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> rows(Map<String,Object> value,String key){Object v=value.get(key);if(v==null){var list=new ArrayList<Map<String,Object>>();value.put(key,list);return list;}if(!(v instanceof List<?> list)||list.stream().anyMatch(item->!(item instanceof Map<?,?>)))throw invalid("Colección UML inválida");return (List<Map<String,Object>>)v;}
    private Map<String,Object> copyMap(Map<?,?> source){var result=new LinkedHashMap<String,Object>();source.forEach((k,v)->{if(!(k instanceof String s))throw invalid("Clave UML inválida");result.put(s,copy(v));});return result;}
    private Object copy(Object v){if(v instanceof Map<?,?> map)return copyMap(map);if(v instanceof List<?> list){var result=new ArrayList<Object>();list.forEach(item->result.add(copy(item)));return result;}if(v==null||v instanceof String||v instanceof Number||v instanceof Boolean)return v;throw invalid("Valor UML inválido");}
    private IllegalArgumentException invalid(String message){return new IllegalArgumentException(message);}
}
