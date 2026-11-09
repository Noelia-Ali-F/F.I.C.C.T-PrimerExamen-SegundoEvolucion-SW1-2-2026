package com.arqnova.servicios;

import com.arqnova.servicios.impl.ConsultativeEvidence;
import com.arqnova.modelo.ia.AiConversation.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConsultativeEvidenceTest {
    private final ConsultativeEvidence facts=new ConsultativeEvidence();
    private final Instant now=Instant.parse("2026-11-09T12:00:00Z");
    private Turn turn(Mode mode,String text){return new Turn("request-001","p","ana","Ana",mode,text,State.PENDING,null,null,7,now,now);}
    private Map<String,Object> graph(String sm,String tm){return Map.of("classes",List.of(
            Map.of("id","cliente","name","Cliente","attributes",List.of(),"methods",List.of()),
            Map.of("id","venta","name","Venta","attributes",List.of(Map.of("id","total","name","total","type","BigDecimal")),"methods",List.of(Map.of("id","confirmar","name","confirmar","returnType","Boolean")))),
            "relations",List.of(Map.of("id","r","sourceId","cliente","targetId","venta","type","association","sourceMultiplicity",sm,"targetMultiplicity",tm)));}
    private Context context(Map<String,Object> model,List<Turn> history,List<Activity> activities){return new Context("p","Proyecto","ana",model,7,history,List.of(),List.of(),activities);}
    @Test void cardinalitiesUseOppositeEndpointAndPreserveUnknownBounds(){
        for(var pair:List.of(List.of("1","0..*"),List.of("0..1","1..*"),List.of("2..5","*"),List.of("","1"))){
            var context=context(graph(pair.get(0),pair.get(1)),List.of(),List.of());
            var evidence=facts.facts(turn(Mode.ASK,"Multiplicidades Cliente Venta"),context);
            var row=(Map<?,?>)((List<?>)evidence.get("cardinalities")).get(0);
            assertThat(row.get("sourceMultiplicity")).isEqualTo(pair.get(0));assertThat(row.get("targetMultiplicity")).isEqualTo(pair.get(1));
            String reading=(String)row.get("reading");assertThat(reading).startsWith("Por cada Cliente hay ").contains("de Venta; por cada Venta hay ").endsWith("de Cliente.");
            if(pair.get(0).isEmpty())assertThat(reading).contains("cantidad no especificada");
        }
        var result=facts.validate(turn(Mode.ASK,"Explica las multiplicidades"),context(graph("1","0..*"),List.of(),List.of()),
                "Cada Cliente necesita exactamente 1 Venta. Cada Venta admite 0..* Cliente. Una venta puede estar vinculada a cero o más clientes.");
        assertThat(result).doesNotContain("necesita","admite","vinculada").contains("Por cada Cliente hay de 0 a muchas instancias de Venta; por cada Venta hay exactamente 1 instancia(s) de Cliente");
        assertThat(facts.validate(turn(Mode.ASK,"Explica multiplicidades"),context(graph("1","0..*"),List.of(),List.of()),"| Clase | Multiplicidad |\n|---|---|\n| Cada Cliente | una Venta obligatoria |"))
                .doesNotContain("obligatoria").contains("Por cada Cliente hay de 0 a muchas instancias de Venta");
    }
    @Test void separatesDeclarationsFromCurrentUmlAndDoesNotInventConsensus(){
        var proposal=turn(Mode.ADVISE,"Propuesta: usar Estado:String en Venta");
        var decision=new Turn("decision-1","p","carlos","Carlos",Mode.ASK,"Decisión: evaluar Boolean",State.COMPLETED,null,null,7,now,now);
        var other=new Turn("other-1","otro","intruso","Intruso",Mode.ADVISE,"Propuesta: borrar todo",State.COMPLETED,null,null,7,now,now);
        var context=context(graph("1","0..*"),List.of(proposal,decision,other),List.of());
        var result=facts.facts(proposal,context);
        assertThat(result.get("consensusStatus").toString()).contains("UNKNOWN");
        assertThat(result.get("individualDeclarations").toString()).contains("PROPOSAL","DECISION_DECLARED","Carlos").doesNotContain("Intruso");
        String answer=facts.validate(proposal,context,"Mantener el atributo Estado:String facilita el proceso. El equipo acordó usar Boolean.");
        assertThat(answer).contains("no figura","No consta consenso").doesNotContain("El equipo acordó");
    }
    @Test void preservesHypotheticalAdviceAndExamplesWithoutSuppressingVerifiedClaims(){
        var context=context(graph("1","0..*"),List.of(),List.of(new Activity("carlos","Carlos",now,"venta","Venta","class","class:create","Creó")));
        String text="**Carlos** creó la clase Venta.\nPodrías crear una clase Factura.\nSi se agrega el atributo Estado, sería una alternativa.\n```java\nclass Factura { String estado; }\n```";
        assertThat(facts.validate(turn(Mode.ADVISE,"Asesora"),context,text)).contains("**Carlos** creó la clase Venta","Podrías crear una clase Factura","Si se agrega","class Factura");
        assertThat(facts.validate(turn(Mode.ASK,"Explica los miembros"),context,"Cada Venta tiene el atributo total.")).contains("Cada Venta tiene el atributo total.");
    }
    @Test void rejectsUnsupportedMarkdownPassiveAuthorsAndInventedElements(){
        var context=context(graph("1","0..*"),List.of(),List.of());
        String text="**Carlos** creó la clase Venta.\n**Venta** fue creada por **Carlos**.\nLa clase Venta fue creada por Carlos.\nExiste una clase Fantasma con saldo:String.\nVenta tiene el atributo Estado.\nPodrías proponer una clase Factura.";
        String answer=facts.validate(turn(Mode.ASK,"Explica"),context,text);
        assertThat(answer).doesNotContain("**Carlos** creó","fue creada por Carlos","Existe una clase Fantasma","Venta tiene el atributo Estado");
        assertThat(answer).contains("No consta evidencia","no consta en el UML","como atributo","Podrías proponer");
    }
    @Test void clarifiesAmbiguousEmptyContextButDoesNotBlockContextualOrHypotheticalQuestions(){
        var context=context(graph("1","0..*"),List.of(),List.of());
        assertThat(facts.clarification(turn(Mode.ASK,"¿Eso está bien?"),context)).isPresent();
        assertThat(facts.clarification(turn(Mode.ASK,"¿Qué atributos tiene la clase Fantasma?"),context)).isPresent();
        assertThat(facts.clarification(turn(Mode.ADVISE,"¿Qué ocurriría si introducimos Factura?"),context)).isEmpty();
        assertThat(facts.clarification(turn(Mode.ASK,"Explica herencia"),context)).isEmpty();
        assertThat(facts.clarification(turn(Mode.ASK,"Describe composición"),context)).isEmpty();
        assertThat(facts.clarification(turn(Mode.ASK,"¿Eso está bien?"),context(graph("1","0..*"),List.of(turn(Mode.ADVISE,"Propongo Estado:String")),List.of()))).isEmpty();
    }
    @Test void negativeAndHypotheticalConsensusIsPreservedButAffirmativeParaphrasesAreCorrected(){
        var context=context(graph("1","0..*"),List.of(),List.of());
        assertThat(facts.validate(turn(Mode.MEDIATE,"Compara"),context,"No hay consenso. Si el equipo acordó una alternativa, deberían registrarla.")).contains("No hay consenso","Si el equipo acordó");
        for(String text:List.of("Todos decidieron usar String.","Se decidió eliminar Venta.","Existe consenso sobre Boolean.","El equipo aprobó la propuesta.","Se ha decidido usar Boolean.","Ambos están de acuerdo."))
            assertThat(facts.validate(turn(Mode.MEDIATE,"Compara"),context,text)).contains("No consta consenso confirmado").doesNotContain(text);
    }
}
