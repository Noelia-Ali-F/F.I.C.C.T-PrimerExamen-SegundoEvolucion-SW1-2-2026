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

}
