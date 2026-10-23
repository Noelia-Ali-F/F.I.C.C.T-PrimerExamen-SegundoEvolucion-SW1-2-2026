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
        interpreter.reconcileImageTitles(raw,Map.of("titles",List.of(Map.of("index",0,"name","Duplicado"),Map.of("index",1,"name","Duplicado"))));assertThat(raw).containsAllEntriesOf(before);
    }
    @Test void rechazaIndicesIncompletosDuplicadosOFraccionarios() {
        for(var titles:List.of(List.of(Map.of("index",0,"name","Otro")),List.of(Map.of("index",0,"name","Otro"),Map.of("index",0,"name","Detalle")),List.of(Map.of("index",0.5,"name","Otro"),Map.of("index",1,"name","Detalle")))) {
            var raw=diagram();var before=new LinkedHashMap<>(raw);interpreter.reconcileImageTitles(raw,Map.of("titles",titles));assertThat(raw).containsAllEntriesOf(before);
        }
    }
    @Test void unTituloIlegibleConservaElNombreOriginal() {
        var raw=diagram();var title=new LinkedHashMap<String,Object>();title.put("index",0);title.put("name",null);
        interpreter.reconcileImageTitles(raw,Map.of("titles",List.of(title,Map.of("index",1,"name","Detalle"))));
        assertThat((List<Map<String,Object>>)raw.get("classes")).extracting(c->c.get("name")).containsExactly("Elemento","Detalle");
    }
    @Test void unRecorteDeOtraClaseNoSustituyeElNombreNiRedirigeRelaciones() {
        for(String name:List.of("Cliente","Usuario","Biblioteca","Reserva")) {
            var raw=new LinkedHashMap<String,Object>(Map.of("classes",List.of(Map.of("name",name),Map.of("name","Detalle")),"relations",List.of(Map.of("sourceClassName",name,"targetClassName","Detalle","type","association"))));
            var before=new LinkedHashMap<>(raw);
            interpreter.reconcileImageTitles(raw,Map.of("titles",List.of(Map.of("index",0,"name","détail"),Map.of("index",1,"name","Produit"))));
            assertThat(raw).containsAllEntriesOf(before);
        }
    }
    @Test void conservaCorreccionDeLetraFinalSinPermitirCambioDeEntidad() {
        var raw=new LinkedHashMap<String,Object>(Map.of("classes",List.of(Map.of("name","Persona"),Map.of("name","Reserva")),"relations",List.of(Map.of("sourceClassName","Persona","targetClassName","Reserva","type","association"))));
        interpreter.reconcileImageTitles(raw,Map.of("titles",List.of(Map.of("index",0,"name","Personal"),Map.of("index",1,"name","Reserva"))));
        assertThat((List<Map<String,Object>>)raw.get("classes")).extracting(c->c.get("name")).containsExactly("Personal","Reserva");
    }

    @Test void noIntercambiaClasesConNombresParecidosAunqueLosRecortesEstenInvertidos() {
        var raw=new LinkedHashMap<String,Object>(Map.of("classes",List.of(Map.of("name","PedidoA"),Map.of("name","PedidoB")),"relations",List.of(Map.of("sourceClassName","PedidoA","targetClassName","PedidoB","type","association"))));
        var before=new LinkedHashMap<>(raw);
        interpreter.reconcileImageTitles(raw,Map.of("titles",List.of(Map.of("index",0,"name","PedidoB"),Map.of("index",1,"name","PedidoA"))));
        assertThat(raw).containsAllEntriesOf(before);
    }

    @Test void laRevisionDeTrazosFinalesNoReasignaTitulosDeOtrosRecuadros() {
        var raw=new LinkedHashMap<String,Object>(Map.of("classes",List.of(Map.of("name","Cliente"),Map.of("name","Detalle"),Map.of("name","Persona")),"relations",List.of(Map.of("sourceClassName","Persona","targetClassName","Cliente","type","association"))));
        interpreter.reconcileFinalTitleStrokes(raw,Map.of("titles",List.of(List.of(0,"détail"),List.of(1,"Produit"),List.of(2,"Personal"))));
        assertThat((List<Map<String,Object>>)raw.get("classes")).extracting(c->c.get("name")).containsExactly("Cliente","Detalle","Personal");
        assertThat(((Map<?,?>)((List<?>)raw.get("relations")).get(0)).get("sourceClassName")).isEqualTo("Personal");
    }

}
