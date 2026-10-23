package com.arqnova.servicios;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
class UmlReviewedImageGraphTest {
 private final OpenAiUmlInterpreter interpreter=new OpenAiUmlInterpreter(new ObjectMapper());
 private Map<String,Object> diagram(){return new LinkedHashMap<>(Map.of("classes",List.of(Map.of("name","Origen","attributes",List.of(Map.of("name","clave"))),Map.of("name","Destino"),Map.of("name","Inscripcion")),"relations",List.of(Map.of("sourceClassName","Origen","targetClassName","Inscripcion","type","association"))));}
 @Test void conservaLaAsociacionOriginalYSuClaseSinTransformarlaEnTablas() {
  var raw=diagram();var classes=raw.get("classes");
  assertThat(interpreter.reconcileReviewedGraph(raw,Map.of("status","READY","relations",List.of(List.of("Origen","Destino","association","participa","*","1..*","Inscripcion"))))).isTrue();
  assertThat(raw.get("classes")).isEqualTo(classes);var relations=(List<Map<String,Object>>)raw.get("relations");assertThat(relations).hasSize(1);assertThat(relations.get(0)).containsEntry("associationClassName","Inscripcion").containsEntry("sourceMultiplicity","*").containsEntry("targetMultiplicity","1..*");
 }
 @Test void noAceptaClasesInventadasNiMultiplicidadesInferidasYEsAtomico() {
  for(var fields:List.of(List.of("Inventada","Destino","association","","1","*",""),List.of("Origen","Destino","association","","muchos","1",""))) {
   var raw=diagram();var before=new LinkedHashMap<>(raw);assertThat(interpreter.reconcileReviewedGraph(raw,Map.of("status","READY","relations",List.of(fields)))).isFalse();assertThat(raw).isEqualTo(before);
  }
 }
 @Test void unaLecturaDudosaNoReemplazaElDiagramaPorUnaSuposicion() {
  var raw=diagram();var before=new LinkedHashMap<>(raw);assertThat(interpreter.reconcileReviewedGraph(raw,Map.of("status","UNCLEAR","relations",List.of()))).isFalse();assertThat(raw).isEqualTo(before);
 }
 @Test void conservaTiposYLosDosExtremosEnDistintosModelos() {
  for(String type:List.of("association","composition","aggregation","inheritance","dependency")) {
   var raw=diagram();assertThat(interpreter.reconcileReviewedGraph(raw,Map.of("status","READY","relations",List.of(List.of("Destino","Origen",type,"","1","0..*",""))))).isTrue();
   var relation=(Map<?,?>)((List<?>)raw.get("relations")).get(0);assertThat(relation.get("sourceClassName")).isEqualTo("Destino");assertThat(relation.get("type")).isEqualTo(type);assertThat(relation.get("targetMultiplicity")).isEqualTo("0..*");
  }
 }
 @Test void noCopiaUnaMultiplicidadDeOtraLineaCuandoElExtremoNoTieneEtiqueta() {
  var raw=diagram();raw.put("relations",List.of(Map.of("sourceClassName","Origen","targetClassName","Destino","type","composition","sourceMultiplicity","1..*","targetMultiplicity","")));
  assertThat(interpreter.reconcileReviewedGraph(raw,Map.of("status","READY","relations",List.of(List.of("Origen","Destino","composition","","1..*","1..*",""))))).isTrue();
  assertThat(((Map<?,?>)((List<?>)raw.get("relations")).get(0)).get("targetMultiplicity")).isEqualTo("");
 }

 private Map<String,Object> triangle(String a,String b,String c){
  return new LinkedHashMap<>(Map.of("classes",List.of(Map.of("name",a),Map.of("name",b),Map.of("name",c)),"relations",List.of(Map.of("sourceClassName",a,"targetClassName",b,"type","association","sourceMultiplicity","*","targetMultiplicity","1..*"),Map.of("sourceClassName",a,"targetClassName",c,"type","association"),Map.of("sourceClassName",c,"targetClassName",b,"type","association"))));
 }
 @Test void confirmaUnionAlCentroConNombresArbitrariosYEliminaSoloLineasNoDibujadas(){
  for(String name:List.of("Participacion","Enrollment","LineaPedido")){
   var raw=triangle("Primera","Segunda",name);var candidates=interpreter.associationAttachmentCandidates(raw);assertThat(candidates).hasSize(1);
   assertThat(interpreter.applyAssociationAttachments(raw,candidates,Map.of("attachments",List.of(List.of(0,"CENTER_DASHED",false,false))))).isTrue();
   var edges=(List<Map<String,Object>>)raw.get("relations");assertThat(edges).hasSize(1);assertThat(edges.get(0)).containsEntry("associationClassName",name).containsEntry("sourceMultiplicity","*").containsEntry("targetMultiplicity","1..*");
  }
 }
 @Test void noConvierteUnTrianguloRealEnClaseDeAsociacion(){
  var raw=triangle("A","B","C");var before=new LinkedHashMap<>(raw);
  assertThat(interpreter.applyAssociationAttachments(raw,interpreter.associationAttachmentCandidates(raw),Map.of("attachments",List.of(List.of(0,"BINARY_LINES",true,true))))).isTrue();assertThat(raw).isEqualTo(before);
 }
 @Test void preservaUnaRelacionPropiaDeLaClaseDeAsociacionSiEstaDibujada(){
  var raw=triangle("A","B","C");assertThat(interpreter.applyAssociationAttachments(raw,interpreter.associationAttachmentCandidates(raw),Map.of("attachments",List.of(List.of(0,"CENTER_DASHED",true,false))))).isTrue();
  var edges=(List<Map<String,Object>>)raw.get("relations");assertThat(edges).hasSize(2);assertThat(edges.get(1)).containsEntry("sourceClassName","A").containsEntry("targetClassName","C");
 }
 @Test void unaUnionIlegibleOReferenciasInvalidasNoModificanElGrafo(){
  for(var row:List.of(List.of(0,"UNCLEAR",false,false),List.of(10,"CENTER_DASHED",false,false))){
   var raw=triangle("A","B","C");var before=new LinkedHashMap<>(raw);assertThat(interpreter.applyAssociationAttachments(raw,interpreter.associationAttachmentCandidates(raw),Map.of("attachments",List.of(row)))).isFalse();assertThat(raw).isEqualTo(before);
  }
 }

 @Test void revisaTambienLaClaseCuandoLaLecturaInicialOmiteUnaOLasDosLineas(){
  for(int count:List.of(0,1)){
   var raw=triangle("A","B","C");var edges=(List<Map<String,Object>>)raw.get("relations");raw.put("relations",new ArrayList<>(edges.subList(0,count+1)));
   var candidates=interpreter.associationAttachmentCandidates(raw);assertThat(candidates).hasSize(1);
   assertThat(interpreter.applyAssociationAttachments(raw,candidates,Map.of("attachments",List.of(List.of(0,"CENTER_DASHED",false,false))))).isTrue();
   assertThat((List<?>)raw.get("relations")).hasSize(1);assertThat((Map<String,Object>)((List<?>)raw.get("relations")).get(0)).containsEntry("associationClassName","C");
  }
 }

 @Test void admiteCorregirUnaLetraEnUnTituloCortoSinCambiarSuIdentidad(){
  for(var names:List.of(List.of("Vente","Venta"),List.of("Cursa","Curso"),List.of("Ordex","Order"))){
   var raw=triangle(names.get(0),"Destino","Inscripcion");
   assertThat(interpreter.reconcileReviewedGraph(raw,Map.of("status","READY","relations",List.of(List.of(names.get(1),"Destino","association","","*","1..*","Inscripcion"))))).isTrue();
   assertThat((Map<String,Object>)((List<?>)raw.get("classes")).get(0)).containsEntry("name",names.get(1));
  }
 }
 @Test void unaLetraNoBastaParaElegirEntreDosClasesDeNombresParecidos(){
  var raw=triangle("Vente","Vento","Destino");var before=new LinkedHashMap<>(raw);
  assertThat(interpreter.reconcileReviewedGraph(raw,Map.of("status","READY","relations",List.of(List.of("Venta","Destino","association","","*","1..*",""))))).isFalse();assertThat(raw).isEqualTo(before);
 }

}
