package com.arqnova.servicios.impl;

import com.arqnova.modelo.ia.AiConversation.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Read-only facts and conservative corrections. No mutation or provider dependencies. */
public final class ConsultativeEvidence {
    private static final int FLAGS=Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE;
    private static final Pattern HYPOTHETICAL=Pattern.compile("\\b(?:si|podri[aá]|podr[ií]as|podr[ií]amos|hip[oó]tesis|hipot[eé]tic[oa]|como propuesta|propone|propongo|proponemos|recomienda|recomiendo|sugiere|sugiero|convendr[ií]a|ser[ií]a|por ejemplo|ejemplo|supongamos|imagina|como alternativa|escenario hipotetico)\\b",FLAGS);
    private static final Pattern CONSENSUS=Pattern.compile("\\b(?:el equipo (?:acord[oó]|decidi[oó]|aprob[oó])|(?:todos|ambos) (?:acordaron|decidieron|aceptaron|est[aá]n de acuerdo|coinciden)|se (?:acord[oó]|decidi[oó]|consensu[oó]|ha acordado|ha decidido)|(?:qued[oó]|ha quedado) (?:acordado|decidido)|(?:hay|hubo|existe|alcanzaron) (?:un )?consenso|hemos (?:acordado|decidido)|(?:acuerdo|decisi[oó]n) (?:final |definitiv[oa] )?del equipo)\\b",FLAGS);
    private static final Pattern EXISTS=Pattern.compile("\\b(?:existe|tenemos|est[aá] definida|ya est[aá] creada|el modelo (?:contiene|incluye|tiene))\\s+(?:(?:una|la)\\s+)?clase\\s+([\\p{L}][\\p{L}\\p{N}_-]*)",FLAGS);
    private static final Pattern AUTHOR=Pattern.compile("(?:^|[.!?]\\s+|^[-•]\\s*)([\\p{L}][\\p{L} .'-]{0,80}?)\\s+(cre[oó]|modific[oó]|actualiz[oó]|elimin[oó]|borr[oó])\\s+(?:la clase|el atributo|el m[eé]todo|la relaci[oó]n)\\s+([\\p{L}0-9_-]+)",FLAGS);
    private static final Pattern PASSIVE=Pattern.compile("\\b(?:(?:la clase|el atributo|el m[eé]todo|la relaci[oó]n)\\s+)?([\\p{L}0-9_-]+)\\s+(?:fue|ha sido)\\s+(cread[oa]|modificad[oa]|actualizad[oa]|eliminad[oa]|borrad[oa])\\s+por\\s+([\\p{L}][\\p{L} .'-]{0,80}?)(?=[.!?;:]|$)",FLAGS);

    public Map<String,Object> facts(Turn turn,Context context){
        var result=new LinkedHashMap<String,Object>();result.put("snapshotVersion",context.diagramVersion());
        result.put("currentFactsSource","Solo Modelo UML actual; propuestas y declaraciones no prueban presencia de elementos.");
        result.put("consensusStatus","UNKNOWN: no hay registro de consenso confirmado; una decisión declarada es individual.");
        result.put("cardinalities",cardinalities(turn,context).stream().limit(6).toList());
        var declarations=new ArrayList<Map<String,Object>>();
        var declarationsSource=new ArrayList<Turn>();declarationsSource.add(turn);declarationsSource.addAll(history(context).stream().filter(t->!t.requestId().equals(turn.requestId())).toList());
        for(var t:declarationsSource){
            String kind=kind(t.instruction());if("INTERVENTION".equals(kind))continue;
            declarations.add(Map.of("kind",kind,"authorId",clip(t.authorId(),128),"authorName",clip(t.authorName(),120),
                    "at",t.createdAt().toString(),"statement",clip(t.instruction(),180),"impliesApplication",false));
            if(declarations.size()==4)break;
        }
        result.put("individualDeclarations",declarations);return result;
    }

    public Optional<String> clarification(Turn turn,Context context){
        String question=plain(turn.instruction());
        if(history(context).isEmpty()&&question.matches("(?:¿)?(?:eso|esto|lo anterior) (?:esta bien|es correcto|que opinas)[?.!]*"))
            return Optional.of("¿A qué clase, relación o propuesta te refieres? No hay antecedentes suficientes para identificar «eso». No se modificó el UML.");
        var match=Pattern.compile("^(?:¿)?(?:(?:que atributos tiene|que metodos tiene) (?:la clase )?|(?:explica|describe) (?:la )?clase )([\\p{L}][\\p{L}\\p{N}_-]*)[?.!]*$",FLAGS).matcher(question);
        if(match.matches()&&!names(context.diagram().get("classes")).contains(normal(match.group(1))))
            return Optional.of("La clase "+match.group(1)+" no consta en el UML vigente (versión "+context.diagramVersion()+"). ¿Te refieres a otra clase o deseas evaluar una propuesta hipotética?");
        return Optional.empty();
    }

    public String validate(Turn turn,Context context,String response){
        StringBuilder out=new StringBuilder();boolean code=false,correctCardinality=false,cardinalityTable=false;
        var facts=cardinalities(turn,context);
        Set<String> classNames=names(context.diagram().get("classes"));
        Set<String> members=new HashSet<>();for(var c:rows(context.diagram().get("classes"))){members.addAll(names(c.get("attributes")));members.addAll(names(c.get("methods")));}
        for(String line:response.split("\\R",-1)){
            if(line.stripLeading().startsWith("```")){code=!code;out.append(line).append('\n');continue;}
            if(code){out.append(line).append('\n');continue;}
            if(!line.contains("|"))cardinalityTable=false;
            if(line.contains("|")&&plain(line).matches(".*\\b(?:multiplicidad|multiplicidades|cardinalidad)\\b.*"))cardinalityTable=true;
            if(cardinalityTable&&!facts.isEmpty()){correctCardinality=true;continue;}
            for(String sentence:line.split("(?<=[.!?])\\s+(?=[\\p{Lu}¿*])")){
            String value=plain(sentence);boolean hypothetical=HYPOTHETICAL.matcher(value).find();String replacement=null;
            if(!hypothetical&&!value.matches(".*\\b(?:no (?:hay|hubo|existe|consta|se|hemos)|sin (?:acuerdo|consenso))\\b.*")&&CONSENSUS.matcher(value).find())
                replacement="No consta consenso confirmado en la evidencia recuperada. Las propuestas y decisiones declaradas pertenecen a sus autores; la decisión del equipo sigue por verificar.";
            var exists=EXISTS.matcher(value);
            if(replacement==null&&!hypothetical&&exists.find()&&!classNames.contains(normal(exists.group(1))))
                replacement="La clase "+exists.group(1)+" no consta en el UML vigente. Indica si deseas discutirla como hipótesis.";
            var actor=AUTHOR.matcher(value);
            if(replacement==null&&!hypothetical&&actor.find()&&!supported(context,actor.group(1),actor.group(2),actor.group(3)))
                replacement="No consta evidencia verificable de esa autoría sobre "+actor.group(3)+" en los antecedentes recuperados.";
            var passive=PASSIVE.matcher(value);
            if(replacement==null&&!hypothetical&&passive.find()&&!supported(context,passive.group(3),passive.group(2),passive.group(1)))
                replacement="No consta evidencia verificable de esa autoría sobre "+passive.group(1)+" en los antecedentes recuperados.";
            // These phrases presuppose that a proposed member already belongs to the snapshot.
            if(replacement==null&&!hypothetical){
                var scoped=Pattern.compile("\\b(?:la clase )?([\\p{L}][\\p{L}\\p{N}_-]*) (?:tiene|contiene|posee|incluye|cuenta con) (?:(?:el|un) )?(atributo|metodo) ([\\p{L}][\\p{L}\\p{N}_-]*)",FLAGS).matcher(value);
                if(scoped.find()){
                    String clazz=scoped.group(1),kind=scoped.group(2),name=scoped.group(3);
                    boolean present=rows(context.diagram().get("classes")).stream().anyMatch(c->normal(clazz).equals(normal((String)c.get("name")))
                            &&names(c.get(kind.equals("metodo")?"methods":"attributes")).contains(normal(name)));
                    // A word before "tiene" may be an adverb ("Venta solo tiene...").
                    // Only validate a scoped member when the captured owner is a known class.
                    if(classNames.contains(normal(clazz))&&!present)replacement="No consta "+name+" como "+kind+" de "+clazz+" en el UML vigente. Puede evaluarse como propuesta, pero no como un cambio aplicado.";
                }
                var member=Pattern.compile("\\b(?:mantener|conservar|cambiar|reemplazar|sustituir|tiene|contiene|posee|incluye)\\s+(?:(?:el|un)\\s+)?(?:atributo|m[eé]todo)\\s+([\\p{L}][\\p{L}\\p{N}_-]*)",FLAGS).matcher(value);
                if(replacement==null&&member.find()&&!members.contains(normal(member.group(1))))replacement="Escenario hipotético, no estructura actual: "+sentence+"\nAclaración: "+member.group(1)+" no figura como miembro del UML vigente; una propuesta no demuestra que se haya aplicado.";
            }
            boolean mentionsEndpoint=facts.stream().anyMatch(f->value.contains(normal((String)f.get("sourceName")))||value.contains(normal((String)f.get("targetName"))));
            boolean mentionsPair=facts.stream().anyMatch(f->value.contains(normal((String)f.get("sourceName")))&&value.contains(normal((String)f.get("targetName"))));
            boolean binding=value.matches(".*\\b(?:asociad[oa]s?|vinculad[oa]s?|relacionad[oa]s?|pertenece|pertenecen|admite|admiten|vincula|relaciona|asocia|tiene|tienen)\\b.*")
                    &&(mentionsPair||value.matches(".*\\b(?:cero|muchos|muchas|varios|varias|exactamente|[0-9]+)\\b.*"));
            boolean describesCardinality=value.matches(".*\\b(?:multiplicidad|multiplicidades|cardinalidad|instancias?|extremo)\\b.*")||(mentionsPair&&value.matches(".*\\bcada\\b.*"));
            if(replacement==null&&!hypothetical&&!facts.isEmpty()&&mentionsEndpoint&&(binding||describesCardinality)){
                // Replace descriptions of actual cardinalities, not code or hypothetical advice.
                correctCardinality=true;replacement="";
            }
            if(replacement==null)out.append(sentence).append('\n');else if(!replacement.isEmpty())out.append(replacement).append('\n');
        }
        }
        boolean asksCardinality=plain(turn.instruction()).matches(".*\\b(?:multiplicidad|multiplicidades|cardinalidad)\\b.*");
        if(correctCardinality||asksCardinality){out.append("\nLectura verificada del UML vigente (versión ").append(context.diagramVersion()).append("):\n");
            for(var fact:facts)out.append("- ").append(fact.get("reading")).append('\n');
        }
        String result=out.toString().strip();return result.isBlank()?"No hay evidencia suficiente para esa afirmación. Especifica la clase o propuesta que deseas evaluar.":result;
    }

    private List<Map<String,Object>> cardinalities(Turn turn,Context context){
        Map<String,String> classes=new HashMap<>();for(var c:rows(context.diagram().get("classes")))if(c.get("id") instanceof String id&&c.get("name") instanceof String name)classes.put(id,name);
        String question=plain(turn.instruction());var result=new ArrayList<Map<String,Object>>();
        for(var r:rows(context.diagram().get("relations"))){if(Set.of("inheritance","dependency").contains(String.valueOf(r.get("type"))))continue;String s=classes.get(r.get("sourceId")),t=classes.get(r.get("targetId"));if(s==null||t==null)continue;
            // General cardinality questions use the first bounded facts; named questions prioritize those endpoints.
            String sm=r.get("sourceMultiplicity") instanceof String v?v:"",tm=r.get("targetMultiplicity") instanceof String v?v:"";
            var fact=new LinkedHashMap<String,Object>();fact.put("relationId",String.valueOf(r.get("id")));fact.put("sourceName",s);fact.put("targetName",t);
            fact.put("sourceMultiplicity",sm);fact.put("targetMultiplicity",tm);
            fact.put("reading","Por cada "+s+" hay "+quantity(tm)+" de "+t+"; por cada "+t+" hay "+quantity(sm)+" de "+s+".");
            fact.put("relevance",(question.contains(normal(s))?1:0)+(question.contains(normal(t))?1:0));result.add(fact);
        }
        result.sort(Comparator.comparingInt((Map<String,Object> v)->(Integer)v.get("relevance")).reversed());return result.stream().limit(6).toList();
    }
    private String quantity(String value){
        if(value.isBlank())return "una cantidad no especificada de instancias";
        if(value.equals("*"))return "de 0 a muchas instancias";
        if(value.matches("\\d+"))return "exactamente "+value+" instancia(s)";
        if(value.matches("\\d+\\.\\.(?:\\d+|\\*)")){String[] b=value.split("\\.\\.");return "de "+b[0]+" a "+(b[1].equals("*")?"muchas":b[1])+" instancias";}
        return "una multiplicidad no interpretable ("+clip(value,24)+")";
    }
    private boolean supported(Context context,String actor,String verb,String element){
        String action=normal(verb).startsWith("cre")?":create":normal(verb).startsWith("elim")||normal(verb).startsWith("borr")?":delete":":update";
        return context.activities().stream().anyMatch(a->a.authorId()!=null&&a.authorName()!=null
                &&(normal(actor).equals(normal(a.authorName()))||normal(actor).equals(normal(a.authorId())))
                &&(normal(element).equals(normal(a.elementId()))||normal(element).equals(normal(a.elementName())))&&a.action()!=null&&a.action().endsWith(action));
    }
    private List<Turn> history(Context context){var result=new LinkedHashMap<String,Turn>();for(var list:List.of(context.groupHistory(),context.userHistory(),context.relevantHistory()))for(var t:list)if(context.projectId().equals(t.projectId()))result.putIfAbsent(t.requestId(),t);return new ArrayList<>(result.values());}
    private String kind(String instruction){String v=plain(instruction).stripLeading();return v.startsWith("decision:")?"DECISION_DECLARED":v.startsWith("propuesta:")||v.startsWith("propongo ")?"PROPOSAL":"INTERVENTION";}
    private static String plain(String value){return normal(value==null?"":value.replaceAll("\\*{1,3}|`|(?<!\\w)_{1,3}|_{1,3}(?!\\w)|^\\s*#{1,6}\\s*","").replaceAll("\\[([^]]+)]\\([^)]*\\)","$1")).strip();}
    private static String normal(String value){return value==null?"":Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);}
    private Set<String> names(Object value){Set<String> result=new HashSet<>();for(var r:rows(value))if(r.get("name") instanceof String name)result.add(normal(name));return result;}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> rows(Object value){return value instanceof List<?> list?list.stream().filter(Map.class::isInstance).map(v->(Map<String,Object>)v).toList():List.of();}
    private String clip(String value,int max){return value==null?"":value.substring(0,Math.min(max,value.length()));}
}
