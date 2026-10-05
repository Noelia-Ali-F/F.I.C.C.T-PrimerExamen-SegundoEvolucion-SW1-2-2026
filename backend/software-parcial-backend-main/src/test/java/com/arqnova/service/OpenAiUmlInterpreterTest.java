package com.arqnova.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.arqnova.dto.request.UmlAiCommandRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiUmlInterpreterTest {
    private HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void conservaComposicionSinMultiplicidadYMetodosConPosicionParaCualquierNombre() {
        var value=new OpenAiUmlInterpreter(mapper);ReflectionTestUtils.setField(value,"model","test");
        for(String whole:List.of("Catalogo","Pedido","Biblioteca")){
            var classes=List.of(Map.of("name",whole,"attributes",List.of(),"methods",List.of(Map.of("name","Buscar por Autor","parameters","","returnType","")),"x",0.1,"y",0.2),Map.of("name","Detalle","attributes",List.of()));
            var result=value.validateImage(Map.of("status","READY","message","ok","warnings",List.of(),"classes",classes,"relations",List.of(Map.of("sourceClassName",whole,"targetClassName","Detalle","type","composition","label","registra","sourceMultiplicity","1...*","targetMultiplicity",""))));
            var relations=(List<Map<String,Object>>)result.get("relations");assertThat(relations).hasSize(1);assertThat(relations.get(0)).containsEntry("type","composition").containsEntry("sourceClassName",whole).containsEntry("sourceMultiplicity","1..*").containsEntry("targetMultiplicity","1");
            var detected=(List<Map<String,Object>>)result.get("classes");assertThat(detected.get(0)).containsEntry("x",0.1).containsEntry("y",0.2);assertThat((List<?>)detected.get(0).get("methods")).hasSize(1);assertThat((List<?>)result.get("warnings")).isNotEmpty();
        }
    }

    @Test void conservaOchoAtributosVisualesSinTipoYRelacion() {
        var value=new OpenAiUmlInterpreter(mapper);
        ReflectionTestUtils.setField(value,"model","gpt-6.1-sol");
        var classes=List.of(Map.of("name","Paciente","attributes",unknown("Id","nombre","Documento","Telefono")),Map.of("name","Turno","attributes",unknown("Id","FechaHora","Estado","motivo")));
        var result=value.validateImage(Map.of("status","READY","message","ok","warnings",List.of(),"classes",classes,"relations",List.of(Map.of("sourceClassName","Paciente","targetClassName","Turno","type","association","label","reserva","sourceMultiplicity","1","targetMultiplicity","0..*"))));
        var detected=(List<Map<String,Object>>)result.get("classes");
        assertThat(detected).hasSize(2);
        for(var clazz:detected) {
            var attrs=(List<Map<String,Object>>)clazz.get("attributes");
            assertThat(attrs).hasSize(4);
            for(var attr:attrs) assertThat(attr).containsEntry("type","").containsEntry("isPrimaryKey",false);
        }
        assertThat((List<?>)result.get("relations")).hasSize(1);
        assertThat((List<?>)result.get("warnings")).hasSize(1);
    }

    @Test void conservaAtributosVisualesMixtos() {
        var value=new OpenAiUmlInterpreter(mapper);
        ReflectionTestUtils.setField(value,"model","test");
        var attrs=new java.util.ArrayList<Map<String,Object>>(unknown("descripcion"));
        attrs.add(Map.of("name","id","type","Long","isPrimaryKey",true));
        attrs.add(Map.of("name","nombre","type","String","isPrimaryKey",false));
        var result=value.validateImage(Map.of("status","READY","message","ok","warnings",List.of(),"classes",List.of(Map.of("name","Producto","attributes",attrs)),"relations",List.of()));
        var clean=(List<Map<String,Object>>)((List<Map<String,Object>>)result.get("classes")).get(0).get("attributes");
        assertThat(clean).hasSize(3);
        assertThat(clean.get(0)).containsEntry("type","");
        assertThat(clean.get(1)).containsEntry("type","Long").containsEntry("isPrimaryKey",true);
    }

    @Test void conservaVinculoYDosAtributosDeClaseDeAsociacionDetectada() {
        var value=new OpenAiUmlInterpreter(mapper);
        ReflectionTestUtils.setField(value,"model","test");
        var classes=List.of(
                Map.of("name","Venta","attributes",unknown("id","fecha")),
                Map.of("name","Producto","attributes",unknown("nombre","precio")),
                Map.of("name","Detalle","attributes",unknown("cantidad","PrecioUnit")));
        var relation=Map.of("sourceClassName","Venta","targetClassName","Producto","type","association","label","",
                "sourceMultiplicity","*","targetMultiplicity","1..*","associationClassName","Detalle");
        var result=value.validateImage(Map.of("status","READY","message","ok","warnings",List.of(),"classes",classes,"relations",List.of(relation)));
        var recognized=(List<Map<String,Object>>)result.get("relations");
        assertThat(recognized).hasSize(1);
        assertThat(recognized.get(0)).containsEntry("associationClassName","Detalle");
        var detail=((List<Map<String,Object>>)result.get("classes")).stream().filter(item->item.get("name").equals("Detalle")).findFirst().orElseThrow();
        assertThat((List<?>)detail.get("attributes")).hasSize(2);
    }
    private List<Map<String,Object>> unknown(String... names) {
        return java.util.Arrays.stream(names).map(name->{Map<String,Object> a=new java.util.LinkedHashMap<>();a.put("name",name);a.put("type",null);a.put("isPrimaryKey",false);return a;}).toList();
    }

    @Test void usaSalidaEstructuradaValidaIdsPkYOperacionesCompuestas() throws Exception {
        String modelJson = """
          {"status":"READY","operations":[
            {"type":"CLASS_CREATE","target":{"className":"Cliente","attributeName":null,"sourceClassName":null,"targetClassName":null},"parameters":{"attributes":[{"name":"id","type":"Long","isPrimaryKey":true},{"name":"correo","type":"String","isPrimaryKey":false}]}},
            {"type":"RELATION_CREATE","target":{"className":null,"attributeName":null,"sourceClassName":"Cliente","targetClassName":"Pedido"},"parameters":{"type":"association","sourceMultiplicity":"1","targetMultiplicity":"0..*","label":"realiza"}}
          ],"clarificationQuestion":null,"message":"Creé Cliente y su relación."}
          """;
        OpenAiUmlInterpreter interpreter = configured(modelJson);
        var current = Map.<String,Object>of("classes", List.of(Map.of("id","pedido","name","Pedido","attributes",List.of())), "relations", List.of());
        var result = interpreter.interpret(new UmlAiCommandRequest("Crea Cliente y relaciónalo con Pedido", current));

        assertThat(result.getInterpreter()).isEqualTo("OPENAI");
        assertThat(result.getOperations()).hasSize(2);
        var create = result.getOperations().get(0);
        var relation = result.getOperations().get(1);
        assertThat(((List<?>) create.getParameters().get("attributes"))).hasSize(2);
        assertThat(((Map<?,?>)((List<?>) create.getParameters().get("attributes")).get(0)).get("isPrimaryKey")).isEqualTo(true);
        assertThat(relation.getParameters().get("sourceId")).isEqualTo(create.getTargetId());
        assertThat(relation.getParameters().get("targetId")).isEqualTo("pedido");
    }

    @Test void rechazaOperacionFueraDelConjuntoCerrado() throws Exception {
        String malicious = """
          {"status":"READY","operations":[{"type":"EXECUTE_SQL","target":{"className":null,"attributeName":null,"sourceClassName":null,"targetClassName":null},"parameters":{"sql":"DROP DATABASE"}}],"clarificationQuestion":null,"message":"ok"}
          """;
        var result = configured(malicious).interpret(new UmlAiCommandRequest("borra todo", Map.of("classes",List.of(),"relations",List.of())));
        assertThat(result.getStatus()).isEqualTo("ERROR");
        assertThat(result.getOperations()).isEmpty();
    }

    @Test void noReenviaLaClaveDentroDelCuerpo() throws Exception {
        final String[] body = {""};
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/responses", exchange -> {
            body[0] = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String structured="{\"status\":\"CLARIFICATION\",\"operations\":[],\"clarificationQuestion\":\"¿Qué deseas crear?\",\"message\":\"Necesito más información.\"}";
            byte[] response=mapper.writeValueAsBytes(Map.of("status","completed","output",List.of(Map.of("type","message","content",List.of(Map.of("type","output_text","text",structured))))));
            exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });server.start();
        OpenAiUmlInterpreter interpreter = interpreter();
        interpreter.interpret(new UmlAiCommandRequest("algo", Map.of("classes",List.of(),"relations",List.of())));
        assertThat(body[0]).doesNotContain("test-secret-key");
        var request = mapper.readTree(body[0]);
        var schema = request.path("text").path("format").path("schema");
        assertThat(request.path("text").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(request.path("reasoning").path("effort").asText()).isEqualTo("low");
        assertThat(request.path("model").asText()).isEqualTo("gpt-6.1-sol");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("properties").path("status").path("enum").toString())
                .isEqualTo("[\"READY\",\"CLARIFICATION\",\"ERROR\"]");
        assertThat(schema.path("properties").path("operations").path("items").path("properties")
                .path("type").path("enum")).hasSize(12);
    }

    @Test void interpretaConOpenAiCrearEditarYEliminarMetodos() throws Exception {
        String modelJson = """
          {"status":"READY","operations":[
            {"type":"METHOD_CREATE","target":{"className":"Usuario","attributeName":null,"methodName":"autenticar","sourceClassName":null,"targetClassName":null},"parameters":{"parameters":"correo: String, clave: String","returnType":"Boolean"}},
            {"type":"METHOD_UPDATE","target":{"className":"Usuario","attributeName":null,"methodName":"autenticar","sourceClassName":null,"targetClassName":null},"parameters":{"name":"validarCredenciales","returnType":"Boolean"}},
            {"type":"METHOD_DELETE","target":{"className":"Usuario","attributeName":null,"methodName":"validarCredenciales","sourceClassName":null,"targetClassName":null},"parameters":{}}
          ],"clarificationQuestion":null,"message":"Métodos actualizados."}
          """;
        var current = Map.<String,Object>of("classes", List.of(Map.of(
                "id","usuario","name","Usuario","attributes",List.of(),"methods",List.of())), "relations", List.of());

        var result = configured(modelJson).interpret(new UmlAiCommandRequest(
                "Agrega autenticar, renómbralo y elimínalo", current));

        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getOperations()).extracting("type")
                .containsExactly("METHOD_CREATE", "METHOD_UPDATE", "METHOD_DELETE");
        assertThat(result.getOperations().get(0).getParameters())
                .containsEntry("classId", "usuario")
                .containsEntry("parameters", "correo: String, clave: String")
                .containsEntry("returnType", "Boolean");
        assertThat(result.getOperations().get(1).getParameters()).containsEntry("name", "validarCredenciales");
        assertThat(result.getOperations().get(2).getParameters()).containsEntry("classId", "usuario");
    }

    @Test void rechazaAclaracionSinPregunta() throws Exception {
        String invalidClarification="""
          {"status":"CLARIFICATION","operations":[],"clarificationQuestion":null,"message":"Necesito aclaración."}
          """;
        var result = configured(invalidClarification).interpret(
                new UmlAiCommandRequest("algo ambiguo", Map.of("classes",List.of(),"relations",List.of())));
        assertThat(result.getStatus()).isEqualTo("ERROR");
        assertThat(result.getMessage()).contains("no contiene una pregunta");
    }

    @Test void validaReconocimientoVisualSinInventarReferencias() {
        OpenAiUmlInterpreter value=new OpenAiUmlInterpreter(mapper);
        ReflectionTestUtils.setField(value,"model","gpt-6.1-sol");
        Map<String,Object> raw=Map.of(
                "status","READY","message","Diagrama reconocido.","warnings",List.of("Revisar texto pequeño."),
                "classes",List.of(
                        Map.of("name","Usuario","attributes",List.of(Map.of("name","id","type","Long","isPrimaryKey",true),Map.of("name","correo","type","String","isPrimaryKey",false))),
                        Map.of("name","Pedido","attributes",List.of(Map.of("name","fecha","type","Date","isPrimaryKey",false)))),
                "relations",List.of(
                        Map.of("sourceClassName","Usuario","targetClassName","Pedido","type","association","label","realiza","sourceMultiplicity","1","targetMultiplicity","0..*"),
                        Map.of("sourceClassName","Fantasma","targetClassName","Pedido","type","association","label","","sourceMultiplicity","1","targetMultiplicity","muchos")));

        Map<String,Object> result=value.validateImage(raw);

        assertThat(result).containsEntry("status","READY").containsEntry("provider","OPENAI");
        assertThat((List<?>)result.get("classes")).hasSize(2);
        assertThat((List<?>)result.get("relations")).hasSize(1);
        assertThat((List<?>)result.get("warnings")).hasSize(2);
    }

    @Test void rechazaImagenSinClasesLegibles() {
        OpenAiUmlInterpreter value=new OpenAiUmlInterpreter(mapper);
        ReflectionTestUtils.setField(value,"model","gpt-6.1-sol");
        Map<String,Object> result=value.validateImage(Map.of("status","READY","message","","warnings",List.of(),"classes",List.of(),"relations",List.of()));
        assertThat(result).containsEntry("status","ERROR");
    }

    @Test void informaCuotaAgotadaSinReintentarNiExponerClave() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/responses", exchange -> {
            calls.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-secret-key");
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        server.start();
        var error = org.junit.jupiter.api.Assertions.assertThrows(OpenAiUmlInterpreter.ProviderException.class,
                () -> interpreter().interpret(new UmlAiCommandRequest("algo", Map.of("classes", List.of(), "relations", List.of()))));
        assertThat(error.httpStatus).isEqualTo(429);
        assertThat(error.getMessage()).doesNotContain("test-secret-key");
        assertThat(calls).hasValue(1);
    }

    @Test void enviaImagenComoEntradaVisualYConservaModeloConfigurado() throws Exception {
        final String[] body = {""};
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/responses", exchange -> {
            body[0] = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String json = "{\"status\":\"READY\",\"classes\":[{\"name\":\"Cliente\",\"attributes\":[]}],\"relations\":[],\"warnings\":[],\"message\":\"ok\"}";
            byte[] result = mapper.writeValueAsBytes(Map.of("status", "completed", "output", List.of(
                    Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", json))))));
            exchange.sendResponseHeaders(200, result.length);
            exchange.getResponseBody().write(result);
            exchange.close();
        });
        server.start();
        var result = interpreter().analyzeImage(new org.springframework.mock.web.MockMultipartFile("image", "uml.png", "image/png", new byte[]{1}));
        assertThat(result).containsEntry("provider", "OPENAI").containsEntry("model", "gpt-6.1-sol");
        var request = mapper.readTree(body[0]);
        assertThat(request.path("input").path(0).path("content").path(1).path("type").asText()).isEqualTo("input_image");
        assertThat(request.path("input").path(0).path("content").path(1).path("image_url").asText()).isEqualTo("data:image/png;base64,AQ==");
        assertThat(request.path("text").path("format").path("strict").asBoolean()).isTrue();
    }

    @Test void ignoraParametrosNulosDeSchemaEstricto() throws Exception {
        var result = configured("{\"status\":\"READY\",\"operations\":[{\"type\":\"ATTRIBUTE_UPDATE\",\"target\":{\"className\":\"Cliente\",\"attributeName\":\"nombre\"},\"parameters\":{\"name\":null,\"type\":\"Text\",\"isPrimaryKey\":null}}],\"clarificationQuestion\":null,\"message\":\"ok\"}")
                .interpret(new UmlAiCommandRequest("cambia tipo", Map.of("classes", List.of(Map.of("id", "c1", "name", "Cliente", "attributes", List.of(Map.of("id", "a1", "name", "nombre", "type", "String")))), "relations", List.of())));
        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getOperations().get(0).getParameters()).containsEntry("type", "Text").doesNotContainKey("name").doesNotContainKey("isPrimaryKey");
    }

    private OpenAiUmlInterpreter configured(String structured) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/responses", exchange -> {
            byte[] response=mapper.writeValueAsBytes(Map.of("status","completed","output",List.of(Map.of("type","message","content",List.of(Map.of("type","output_text","text",structured))))));
            exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });server.start();return interpreter();
    }
    private OpenAiUmlInterpreter interpreter() {
        OpenAiUmlInterpreter value=new OpenAiUmlInterpreter(mapper);
        ReflectionTestUtils.setField(value,"apiKey","test-secret-key");
        ReflectionTestUtils.setField(value,"model","gpt-6.1-sol");
        ReflectionTestUtils.setField(value,"baseUrl","http://127.0.0.1:"+server.getAddress().getPort());
        ReflectionTestUtils.setField(value,"timeoutSeconds",5L);
        ReflectionTestUtils.setField(value,"reasoningEffort","low");
        return value;
    }
}
