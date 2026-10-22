package com.arqnova.servicios;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class UmlImageTitlesTest {
    private final OpenAiUmlInterpreter interpreter=new OpenAiUmlInterpreter(new ObjectMapper());
    private Map<String,Object> diagram() {
        return new LinkedHashMap<>(Map.of("classes",List.of(Map.of("name","Elemento"),Map.of("name","Detalle")),"relations",List.of(Map.of("sourceClassName","Elemento","targetClassName","Detalle","associationClassName","Elemento","type","composition","sourceMultiplicity","1","targetMultiplicity","*"))));
    }
    @Test void corrigeTitulosYReferenciasSinCambiarTipoNiMultiplicidades() {
        var raw=diagram();interpreter.reconcileImageTitles(raw,Map.of("titles",List.of(Map.of("index",0,"name","Elementos"),Map.of("index",1,"name","Detalle"))));
        var relations=(List<Map<String,Object>>)raw.get("relations");
        assertThat(relations.get(0)).containsEntry("sourceClassName","Elementos").containsEntry("associationClassName","Elementos").containsEntry("type","composition").containsEntry("sourceMultiplicity","1").containsEntry("targetMultiplicity","*");
        assertThat((List<Map<String,Object>>)raw.get("classes")).extracting(c->c.get("name")).containsExactly("Elementos","Detalle");
    }
    @Test void rechazaTitulosDuplicadosSinModificarElDiagrama() {
        var raw=diagram();var before=new LinkedHashMap<>(raw);
        interpreter.reconcileImageTitles(raw,Map.of("titles",List.of(Map.of("index",0,"name","Duplicado"),Map.of("index",1,"name","Duplicado"))));assertThat(raw).isEqualTo(before);
    }
    @Test void rechazaIndicesIncompletosDuplicadosOFraccionarios() {
        for(var titles:List.of(List.of(Map.of("index",0,"name","Otro")),List.of(Map.of("index",0,"name","Otro"),Map.of("index",0,"name","Detalle")),List.of(Map.of("index",0.5,"name","Otro"),Map.of("index",1,"name","Detalle")))) {
            var raw=diagram();var before=new LinkedHashMap<>(raw);interpreter.reconcileImageTitles(raw,Map.of("titles",titles));assertThat(raw).isEqualTo(before);
        }
    }
    @Test void unTituloIlegibleConservaElNombreOriginal() {
        var raw=diagram();var title=new LinkedHashMap<String,Object>();title.put("index",0);title.put("name",null);
        interpreter.reconcileImageTitles(raw,Map.of("titles",List.of(title,Map.of("index",1,"name","Detalle"))));
        assertThat((List<Map<String,Object>>)raw.get("classes")).extracting(c->c.get("name")).containsExactly("Elemento","Detalle");
    }
}
