package com.arqnova.servicios;

import com.arqnova.modelo.ia.AiConversation.*;
import com.arqnova.servicios.impl.UmlReviewEvidence;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class UmlReviewEvidenceTest {
    private final UmlReviewEvidence review=new UmlReviewEvidence();
    private final Instant at=Instant.parse("2026-11-09T12:00:00Z");
    private Turn request(String instruction){return new Turn("review-001","p","ana","Ana",Mode.REVIEW,instruction,State.PENDING,null,null,3,at,at);}
    private Map<String,Object> clazz(String id,String name,List<?> attributes,List<?> methods){return Map.of("id",id,"name",name,"attributes",attributes,"methods",methods);}
    private Map<String,Object> method(String id,String name,String parameters){return Map.of("id",id,"name",name,"parameters",parameters,"returnType","Boolean");}
    private Map<String,Object> attribute(String id,String name){return Map.of("id",id,"name",name,"type","String");}
    private Context context(List<?> classes,List<?> relations){return new Context("p","Proyecto","ana",Map.of("classes",classes,"relations",relations),3,List.of(),List.of());}
    private Context model(){return context(List.of(clazz("venta","Venta",List.of(attribute("total","total")),List.of(method("confirmar","confirmar",""))),clazz("cliente","Cliente",List.of(),List.of())),List.of(Map.of("id","r","type","association","sourceId","cliente","targetId","venta","sourceMultiplicity","1","targetMultiplicity","0..*")));}
    private Answer answer(Context context,Finding... findings){return review.review(request("Revisa el modelo"),context,List.of(findings),"FAKE","simulation");}
    @Test void unsupportedParameterErrorIsCorrectedToSnapshotCount(){
        var result=answer(model(),new Finding("ERROR","confirmar","El método confirmar tiene siete parámetros duplicados."));
        assertThat(result.findings()).allMatch(f->!f.severity().equals("ERROR"));
        assertThat(result.findings().get(0).message()).contains("0 parámetros").doesNotContain("siete");
    }
    @Test void classQuantitiesAndIncidentRelationsComeFromSnapshot(){
        var result=answer(model(),new Finding("ERROR","venta","Venta tiene siete atributos, ocho métodos y nueve relaciones."));
        assertThat(result.findings().get(0).message()).contains("1 atributos, 1 métodos y 1 relaciones");
        assertThat(result.findings().get(0).severity()).isEqualTo("INFO");
    }
    @Test void nullIdCannotLaunderNonexistentClassButNamedMembersResolve(){
        var result=answer(model(),new Finding("ERROR",null,"La clase Fantasma carece de métodos."),new Finding("ERROR",null,"El método confirmar tiene 7 parámetros."));
        assertThat(result.findings()).noneMatch(f->f.severity().equals("ERROR"));
        assertThat(result.findings().get(0).message()).contains("No consta").doesNotContain("carece");
        assertThat(result.findings().get(1).message()).contains("0 parámetros");
        assertThat(result.findings().get(1).elementId()).isEqualTo("confirmar");
    }
    @Test void invalidIdsDoNotDiscardValidFindingsOrBecomeErrors(){
        var result=answer(model(),new Finding("ERROR","missing","No existe"),new Finding("WARNING","venta","Podrías separar responsabilidades."));
        assertThat(result.findings()).hasSize(2).noneMatch(f->f.severity().equals("ERROR"));
        assertThat(result.findings().get(1).message()).contains("Recomendación/opinión","Podrías separar");
    }
    @Test void duplicatesAndRepeatedParameterNamesAreDemonstratedButOverloadsAreNotDuplicates(){
        var graph=context(List.of(clazz("venta","Venta",List.of(attribute("a1","total"),attribute("a2","total")),List.of(method("m1","guardar","x:String"),method("m2","guardar","y:String"),method("m3","guardar","x:int"),method("m4","repetir","x:String,x:int")))),List.of());
        var result=answer(graph);
        assertThat(result.findings()).filteredOn(f->f.severity().equals("ERROR")).extracting(Finding::elementId).containsExactlyInAnyOrder("a2","m2","m4");
    }
    @Test void validModelDoesNotReceiveInventedDeterministicErrors(){assertThat(answer(model()).findings()).isEmpty();}
    @Test void cardinalRecommendationsIdentifyBothExtremesInsteadOfPreservingInversion(){
        var result=answer(model(),new Finding("ERROR","r","Por cada Venta hay un Cliente, por lo que cambia el extremo destino a 1."));
        String message=result.findings().get(0).message();
        assertThat(message).contains("origen Cliente [sourceMultiplicity=1] cuenta Cliente por cada Venta","destino Venta [targetMultiplicity=0..*] cuenta Venta por cada Cliente","para cambiar cuántos Venta hay por Cliente, el extremo Venta");
        assertThat(message).doesNotContain("cambia el extremo destino a 1","Lectura verificada");
        assertThat(result.findings().get(0).severity()).isEqualTo("INFO");
    }
    @Test void missingMultiplicityKeepsItsSpecificWarning(){
        var c=model();var relation=new LinkedHashMap<>((Map<String,Object>)((List<?>)c.diagram().get("relations")).get(0));relation.put("targetMultiplicity","");
        var graph=context((List<?>)c.diagram().get("classes"),List.of(relation));
        assertThat(answer(graph).findings()).anySatisfy(f->{assertThat(f.severity()).isEqualTo("WARNING");assertThat(f.message()).contains("no especificada en destino Venta","targetMultiplicity=no especificada");});
    }
    @Test void declarationsAreLiteralAuthoredProjectScopedAndNotReinterpretedAsMethodChanges(){
        var c=model();var carlos=new Turn("proposal-carlos","p","carlos","Carlos",Mode.ADVISE,"Propuesta: Estado:String en Venta",State.COMPLETED,null,null,3,at,at);
        var intruder=new Turn("proposal-other","other","intruder","Intruso",Mode.ADVISE,"Propuesta: borrar todo",State.COMPLETED,null,null,3,at,at);
        var context=new Context("p","Proyecto","ana",c.diagram(),3,List.of(carlos,intruder),List.of());
        var result=review.review(request("Propongo Confirmada:Boolean en Venta"),context,List.of(new Finding("INFO",null,"Ana propone cambiar el método confirmar para devolver Confirmada:Boolean. Todos acordaron aceptarlo.")),"FAKE","simulation");
        assertThat(result.text()).contains("Carlos","Propuesta: Estado:String en Venta","Ana","Propongo Confirmada:Boolean en Venta","No acredita aplicación ni consenso").doesNotContain("Intruso","cambiar el método");
        assertThat(result.findings().get(0).message()).doesNotContain("Todos acordaron","cambiar el método");
    }
    @Test void emptyAndUnknownTargetsClarifyWithoutProviderOrFakeFindings(){
        var empty=review.local(request("Revisa todo"),context(List.of(),List.of())).orElseThrow();assertThat(empty.provider()).isEqualTo("LOCAL");assertThat(empty.findings()).isEmpty();
        assertThat(review.local(request("Revisa la clase Fantasma"),model()).orElseThrow().text()).contains("no consta");
        assertThat(review.local(request("Revisa el método confirmar"),model())).isEmpty();
        assertThat(review.local(request("Revisa el atributo Venta"),model())).isPresent();
        assertThat(review.local(request("Revisa la relación Cliente–Venta"),model())).isEmpty();
        assertThat(review.local(request("Revisa la clase Venta y sus métodos"),model())).isEmpty();
    }
    @Test void unknownAuthorDoesNotBecomeFactAndRecommendationsRemainAdvisory(){
        var result=answer(model(),new Finding("ERROR","venta","**Carlos** creó la clase Venta."),new Finding("WARNING","venta","Podrías separar responsabilidades para reducir acoplamiento."));
        assertThat(result.findings().get(0).message()).contains("No consta evidencia");
        assertThat(result.findings().get(1).message()).contains("Recomendación/opinión","reducir acoplamiento");assertThat(result.operations()).isEmpty();
        var hypothetical=answer(model(),new Finding("INFO",null,"Podrías agregar el atributo Estado a Venta."));
        assertThat(hypothetical.findings().get(0).message()).contains("hipotética","Podrías agregar el atributo Estado");
        assertThat(answer(model(),new Finding("INFO","venta","Se recomienda separar responsabilidades.")).findings().get(0).message()).contains("Recomendación/opinión","Se recomienda separar responsabilidades");
        assertThat(answer(model(),new Finding("INFO",null,"Diego sugiere crear la clase Factura.")).findings().get(0).message()).doesNotContain("Diego sugiere");
    }
    @Test void genericParameterTypesDoNotInflateCounts(){
        var graph=context(List.of(clazz("v","Venta",List.of(),List.of(method("m","guardar","valores:Map<String,Integer>, activo:Boolean")))),List.of());
        assertThat(answer(graph,new Finding("ERROR","m","El método guardar tiene siete parámetros.")).findings().get(0).message()).contains("2 parámetros");
    }
    @Test void namesWithUnderscoresAreDistinctAndMetadataIsNotInvented(){
        var graph=context(List.of(clazz("a","Orden_Venta",List.of(),List.of()),clazz("b","OrdenVenta",List.of(),List.of())),List.of());
        assertThat(answer(graph).findings()).isEmpty();assertThat(answer(graph).text()).doesNotContain("Responsabilidad","descripción inventada");
    }
    @Test void namedAndQuotedReferencesDoNotInventAnElementCalledLlamado(){
        var result=answer(model(),new Finding("ERROR",null,"El atributo llamado total tiene un tipo incorrecto."));
        assertThat(result.findings().get(0).message()).doesNotContain("elemento llamado");
        assertThat(result.findings().get(0).elementId()).isEqualTo("total");
    }
    @Test void relationLabelsCannotBypassCardinalEndpointValidation(){
        var c=model();var relation=new LinkedHashMap<>((Map<String,Object>)((List<?>)c.diagram().get("relations")).get(0));relation.put("label","realiza");relation.put("targetMultiplicity","");
        var graph=context((List<?>)c.diagram().get("classes"),List.of(relation));
        assertThat(review.local(request("Revisa la relación realiza"),graph)).isEmpty();
        var result=answer(graph,new Finding("INFO",null,"Relación \"realiza\" indica que cada Cliente está asociado a una o más Ventas. Se recomienda definir la multiplicidad del destino."));
        assertThat(result.findings()).noneMatch(f->f.message().contains("asociado a una o más"));
        assertThat(result.findings()).anyMatch(f->f.message().contains("targetMultiplicity=no especificada")&&f.message().contains("origen Cliente"));
    }
}
