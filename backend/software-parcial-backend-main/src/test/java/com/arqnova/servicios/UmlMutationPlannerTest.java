package com.arqnova.servicios;

import com.arqnova.modelo.dto.response.UmlAiOperationResponse;
import com.arqnova.servicios.impl.UmlMutationPlanner;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UmlMutationPlannerTest {
    private final UmlMutationPlanner planner=new UmlMutationPlanner();
    private UmlAiOperationResponse op(String type,String target,Map<String,Object> p){return UmlAiOperationResponse.builder().type(type).targetId(target).parameters(p).build();}
    private Map<String,Object> node(String id,String name){return Map.of("id",id,"name",name,"x",70,"y",100,"attributes",List.of(),"methods",List.of());}
    private Map<String,Object> graph(){return Map.of("format","uml-class-diagram","version",2,"classes",List.of(node("a","Venta"),node("b","Producto")),"relations",List.of(),"customMetadata",Map.of("keep",true));}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> rows(Map<String,Object> model,String key){return (List<Map<String,Object>>)model.get(key);}
    private UmlAiOperationResponse relation(String type,String source,String target){return op("RELATION_CREATE",null,Map.of("id","r","sourceId",source,"targetId",target,"type",type,"sourceMultiplicity","*","targetMultiplicity","1..*","associationClassName","Detalle"));}

    @Test void preservesOriginalAndAppliesManyToManyAsAssociationClass(){
        var original=graph();var plan=planner.plan(original,List.of(relation("association","a","b")),"request-001");
        assertThat(rows(original,"relations")).isEmpty();assertThat(rows(original,"classes")).hasSize(2);
        assertThat(plan.model().get("customMetadata")).isEqualTo(original.get("customMetadata"));
        var relation=rows(plan.model(),"relations").get(0);var detail=rows(plan.model(),"classes").get(2);
        assertThat(relation).containsEntry("sourceMultiplicity","*").containsEntry("targetMultiplicity","1..*").containsEntry("associationClassId",detail.get("id"));
        assertThat(detail).containsEntry("name","Detalle").containsEntry("stereotype","Clase de asociación").containsEntry("color","#2563eb");
        assertThat(rows(plan.model(),"relations")).hasSize(1);
        assertThat(plan.affected()).contains(new UmlMutationPlanner.Element("class","a"),new UmlMutationPlanner.Element("class","b"));
    }
    @Test void preservesCompositionAndAllOtherRelationTypesWithoutInventingAssociationClasses(){
        for(String type:List.of("composition","aggregation","inheritance","dependency")){
            var model=planner.plan(graph(),List.of(relation(type,"a","b")),"request-002").model();
            assertThat(rows(model,"classes")).hasSize(2);assertThat(rows(model,"relations").get(0)).containsEntry("type",type).doesNotContainKey("associationClassId");
        }
    }
    @Test void batchCanReferenceNewClassesAndIsDeterministic(){
        var ops=List.of(op("CLASS_CREATE",null,Map.of("id","c","name","Cliente","attributes",List.of(Map.of("name","id","type","Long","isPrimaryKey",true)))),
                op("ATTRIBUTE_CREATE",null,Map.of("classId","c","name","email")),op("METHOD_CREATE",null,Map.of("classId","c","name","comprar","returnType","void")),
                op("RELATION_CREATE",null,Map.of("sourceId","c","targetId","a","sourceMultiplicity","1","targetMultiplicity","0..*")));
        var result=planner.plan(graph(),ops,"request-003");
        assertThat(result).isEqualTo(planner.plan(graph(),ops,"request-003"));
        var cliente=rows(result.model(),"classes").get(2);
        assertThat(rows(cliente,"attributes")).hasSize(2);assertThat(rows(cliente,"methods")).hasSize(1);
        assertThat(rows(result.model(),"relations")).hasSize(1);
    }
    @Test void invalidLastOperationLeavesOriginalUntouched(){
        var original=graph();var expected=graph();
        assertThatThrownBy(()->planner.plan(original,List.of(op("CLASS_UPDATE","a",Map.of("name","VentaEditada")),relation("composition","a","missing")),"request-004")).isInstanceOf(IllegalArgumentException.class);
        assertThat(original).isEqualTo(expected);
    }
    @Test void rejectsInvalidMultiplicitiesUnknownOperationsAndDuplicateClassNames(){
        for(String multiplicity:List.of("muchos","5..2","-1","1..","999999999999999999"))assertThatThrownBy(()->planner.plan(graph(),List.of(op("RELATION_CREATE",null,Map.of("sourceId","a","targetId","b","sourceMultiplicity",multiplicity))),"request-005")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->planner.plan(graph(),List.of(op("RUN_SQL",null,Map.of())),"request-005")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->planner.plan(graph(),List.of(op("CLASS_CREATE",null,Map.of("name"," vénta "))),"request-005")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void updatesAndDeletesMembersWithoutChangingClassIdentity(){
        var create=planner.plan(graph(),List.of(op("ATTRIBUTE_CREATE","attr",Map.of("classId","a","name","fecha")),op("METHOD_CREATE","method",Map.of("classId","a","name","guardar"))),"request-006").model();
        var updated=planner.plan(create,List.of(op("ATTRIBUTE_UPDATE","attr",Map.of("classId","a","type","Date")),op("METHOD_UPDATE","method",Map.of("classId","a","returnType","Boolean"))),"request-007").model();
        assertThat(rows(rows(updated,"classes").get(0),"attributes").get(0)).containsEntry("type","Date");
        assertThat(rows(rows(updated,"classes").get(0),"methods").get(0)).containsEntry("returnType","Boolean");
        var deleted=planner.plan(updated,List.of(op("ATTRIBUTE_DELETE","attr",Map.of("classId","a")),op("METHOD_DELETE","method",Map.of("classId","a"))),"request-008").model();
        assertThat(rows(rows(deleted,"classes").get(0),"attributes")).isEmpty();assertThat(rows(rows(deleted,"classes").get(0),"methods")).isEmpty();
        assertThat(rows(deleted,"classes").get(0)).containsEntry("id","a").containsEntry("x",70).containsEntry("y",100);
    }
    @Test void deletingEndpointOrRelationAlsoRemovesGeneratedAssociationClass(){
        var model=planner.plan(graph(),List.of(relation("association","a","b")),"request-009").model();
        var deleted=planner.plan(model,List.of(op("CLASS_DELETE","a",Map.of())),"request-010").model();
        assertThat(rows(deleted,"classes")).hasSize(1);assertThat(rows(deleted,"relations")).isEmpty();
        var relationDeleted=planner.plan(model,List.of(op("RELATION_DELETE","r",Map.of())),"request-011").model();
        assertThat(rows(relationDeleted,"classes")).hasSize(2);assertThat(rows(relationDeleted,"relations")).isEmpty();
    }
    @Test void updatingToManyToManyCreatesOnlyOneAssociationClass(){
        var model=planner.plan(graph(),List.of(op("RELATION_CREATE",null,Map.of("id","r","sourceId","a","targetId","b"))),"request-012").model();
        var update=op("RELATION_UPDATE","r",Map.of("sourceMultiplicity","0..*","targetMultiplicity","1..*"));
        var result=planner.plan(model,List.of(update,update),"request-013").model();
        assertThat(rows(result,"classes")).hasSize(3);assertThat(rows(result,"relations")).hasSize(1);
    }
    @Test void refusesUnknownTargetsAndDoesNotAllowProviderToOverwriteCollectionsOrIds(){
        assertThatThrownBy(()->planner.plan(graph(),List.of(op("CLASS_DELETE","missing",Map.of())),"request-014")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->planner.plan(graph(),List.of(op("CLASS_UPDATE","a",Map.of("id","other","attributes",List.of(Map.of("name","injected")),"name","VentaNueva"))),"request-014")).isInstanceOf(IllegalArgumentException.class);
        var model=planner.plan(graph(),List.of(op("CLASS_UPDATE","a",Map.of("name","VentaNueva"))),"request-014").model();
        assertThat(rows(model,"classes").get(0)).containsEntry("id","a").containsEntry("name","VentaNueva").containsEntry("attributes",List.of());
    }
    @Test void reusesExplicitAssociationClassWithItsAttributesInsteadOfDuplicatingIt(){
        var detail=op("CLASS_CREATE","detalle",Map.of("name","Detalle","attributes",List.of(Map.of("name","cantidad","type","Integer"))));
        var model=planner.plan(graph(),List.of(detail,relation("association","a","b")),"request-015").model();
        assertThat(rows(model,"classes")).hasSize(3);
        assertThat(rows(model,"relations").get(0)).containsEntry("associationClassId","detalle");
        assertThat(rows(rows(model,"classes").get(2),"attributes").get(0)).containsEntry("name","cantidad");
    }

    @Test void includesEveryCascadedRelationAndAttributeInAffectedElements(){
        var relation=new HashMap<String,Object>();relation.putAll(Map.of("id","r","sourceId","a","targetId","b","type","association","sourceMultiplicity","*","targetMultiplicity","*","associationClassId","c"));
        var c=new HashMap<>(node("c","Detalle"));c.put("attributes",List.of(Map.of("id","quantity","name","cantidad","type","Integer")));
        var original=Map.<String,Object>of("classes",List.of(node("a","Venta"),node("b","Producto"),c,node("d","Otra")),"relations",List.of(relation,Map.of("id","collateral","sourceId","c","targetId","d","type","association","sourceMultiplicity","1","targetMultiplicity","1")));
        var result=planner.plan(original,List.of(op("CLASS_DELETE","a",Map.of())),"request-cascade");
        assertThat(rows(result.model(),"relations")).isEmpty();
        assertThat(result.affected()).contains(new UmlMutationPlanner.Element("relation","collateral"),new UmlMutationPlanner.Element("attribute","quantity"));
    }
    @Test void preservesUnspecifiedImportedMultiplicitiesWhileRejectingInvalidSpecifiedBounds(){
        var original=Map.<String,Object>of("classes",List.of(node("a","A"),node("b","B")),"relations",List.of(Map.of("id","r","sourceId","a","targetId","b","type","association","sourceMultiplicity","","targetMultiplicity","")));
        var result=planner.plan(original,List.of(op("ATTRIBUTE_CREATE",null,Map.of("classId","a","name","email","type","String"))),"request-imported");
        assertThat(rows(result.model(),"relations").get(0)).containsEntry("sourceMultiplicity","").containsEntry("targetMultiplicity","");
        assertThatThrownBy(()->planner.plan(original,List.of(op("RELATION_UPDATE","r",Map.of("sourceMultiplicity","3..1"))),"request-invalid")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsCyclesExactDuplicatesAndUnknownParametersButAllowsDistinctAssociations(){
        for(String type:List.of("inheritance","composition")){
            assertThatThrownBy(()->planner.plan(graph(),List.of(op("RELATION_CREATE","r1",Map.of("sourceId","a","targetId","b","type",type)),op("RELATION_CREATE","r2",Map.of("sourceId","b","targetId","a","type",type))),"request-cycle")).isInstanceOf(IllegalArgumentException.class);
        }
        var one=op("RELATION_CREATE","r1",Map.of("sourceId","a","targetId","b"));
        assertThatThrownBy(()->planner.plan(graph(),List.of(one,op("RELATION_CREATE","r2",Map.of("sourceId","a","targetId","b"))),"request-duplicate")).isInstanceOf(IllegalArgumentException.class);
        assertThat(rows(planner.plan(graph(),List.of(one,op("RELATION_CREATE","r2",Map.of("sourceId","a","targetId","b","label","otra finalidad"))),"request-parallel").model(),"relations")).hasSize(2);
        assertThatThrownBy(()->planner.plan(graph(),List.of(op("ATTRIBUTE_CREATE",null,Map.of("classId","a","name","email","unsupported",true))),"request-unknown")).isInstanceOf(IllegalArgumentException.class);
        assertThat(rows(planner.plan(graph(),List.of(op("CLASS_UPDATE","a",Map.of("x",99))),"request-move").model(),"classes").get(0)).containsEntry("x",99);
    }
}
