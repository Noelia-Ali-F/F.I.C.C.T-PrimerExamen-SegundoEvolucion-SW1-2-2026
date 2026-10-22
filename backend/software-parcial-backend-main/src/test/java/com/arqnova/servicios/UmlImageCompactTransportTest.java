package com.arqnova.servicios;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
class UmlImageCompactTransportTest {
 @Test void reconstruyeClasesYAtributosSinCambiarTextoNiTipos(){
  var input=Map.<String,Object>of("classes",List.of(Arrays.asList("Personal",List.of(Arrays.asList("a",null,false)),List.of(Arrays.asList("actualizar","n: String",null)),.1,.2)),"relations",List.of());
  var result=UmlAiProviderPipeline.expandTupleImage(input);var clazz=(Map<?,?>)((List<?>)result.get("classes")).get(0);
  assertThat(clazz.get("name")).isEqualTo("Personal");var expectedAttribute=new LinkedHashMap<String,Object>();expectedAttribute.put("name","a");expectedAttribute.put("type",null);expectedAttribute.put("isPrimaryKey",false);assertThat(clazz.get("attributes")).isEqualTo(List.of(expectedAttribute));assertThat(clazz.get("x")).isEqualTo(.1);
 }
 @Test void reconstruyeComposicionYClaseDeAsociacion(){
  var result=UmlAiProviderPipeline.expandTupleImage(Map.of("classes",List.of(),"relations",List.of(List.of("Padre","Hijo","composition","contiene","1","*","Detalle"))));
  assertThat(result.get("relations")).isEqualTo(List.of(Map.of("sourceClassName","Padre","targetClassName","Hijo","type","composition","sourceMultiplicity","1","targetMultiplicity","*","associationClassName","Detalle","label","contiene")));
 }
 @Test void noCompletaTuplasTruncadasConDatosInventados(){
  var input=Map.<String,Object>of("classes",List.of(List.of("Incompleta")),"relations",List.of(List.of("Origen")));assertThat(UmlAiProviderPipeline.expandTupleImage(input)).isEqualTo(input);
 }
 @Test void aceptaRespuestaDelProveedorConObjetosOriginales(){
  var input=Map.<String,Object>of("classes",List.of(Map.of("name","A","attributes",List.of())),"relations",List.of(),"warnings",List.of("Revisar título"));assertThat(UmlAiProviderPipeline.expandTupleImage(input)).isEqualTo(input);
 }
}
