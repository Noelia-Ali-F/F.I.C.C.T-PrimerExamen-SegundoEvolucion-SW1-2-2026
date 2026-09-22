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

class GeminiUmlInterpreterTest {
    private HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void conservaOchoAtributosVisualesSinTipoYRelacion() {
        var value=new GeminiUmlInterpreter(mapper);
        ReflectionTestUtils.setField(value,"model","gemini-3.8-flash");
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
        var value=new GeminiUmlInterpreter(mapper);
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
    private List<Map<String,Object>> unknown(String... names) {
        return java.util.Arrays.stream(names).map(name->{Map<String,Object> a=new java.util.LinkedHashMap<>();a.put("name",name);a.put("type",null);a.put("isPrimaryKey",false);return a;}).toList();
    }

    @Test void imagen503ReintentaYTerminaEnTimeout() throws Exception {
        server=HttpServer.create(new InetSocketAddress(0),0);
        var value=interpreter();
        var client=org.mockito.Mockito.mock(java.net.http.HttpClient.class);
        java.net.http.HttpResponse<String> response=org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
        org.mockito.Mockito.when(response.statusCode()).thenReturn(503);
        org.mockito.Mockito.when(client.send(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response,response,response).thenThrow(new java.net.http.HttpTimeoutException("request timed out"));
        ReflectionTestUtils.setField(value,"http",client);
        org.junit.jupiter.api.Assertions.assertThrows(java.net.http.HttpTimeoutException.class,()->value.analyzeImage(new org.springframework.mock.web.MockMultipartFile("image","uml.png","image/png",new byte[]{1})));
        org.mockito.Mockito.verify(client,org.mockito.Mockito.times(4)).send(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<String>>any());
    }

    @Test void imagen429AgotaCuatroIntentos() throws Exception {
        server=HttpServer.create(new InetSocketAddress(0),0);
        var value=interpreter();
        var client=org.mockito.Mockito.mock(java.net.http.HttpClient.class);
        java.net.http.HttpResponse<String> response=org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
        org.mockito.Mockito.when(response.statusCode()).thenReturn(429);
        org.mockito.Mockito.when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of("Retry-After",List.of("0")),(a,b)->true));
        org.mockito.Mockito.when(client.send(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        ReflectionTestUtils.setField(value,"http",client);
        var error=org.junit.jupiter.api.Assertions.assertThrows(GeminiUmlInterpreter.ProviderException.class,()->value.analyzeImage(new org.springframework.mock.web.MockMultipartFile("image","uml.png","image/png",new byte[]{1})));
        assertThat(error.httpStatus).isEqualTo(429);
    }

    @Test void usaSalidaEstructuradaValidaIdsPkYOperacionesCompuestas() throws Exception {
        String modelJson = """
          {"status":"READY","operations":[
            {"type":"CLASS_CREATE","target":{"className":"Cliente","attributeName":null,"sourceClassName":null,"targetClassName":null},"parameters":{"attributes":[{"name":"id","type":"Long","isPrimaryKey":true},{"name":"correo","type":"String","isPrimaryKey":false}]}},
            {"type":"RELATION_CREATE","target":{"className":null,"attributeName":null,"sourceClassName":"Cliente","targetClassName":"Pedido"},"parameters":{"type":"association","sourceMultiplicity":"1","targetMultiplicity":"0..*","label":"realiza"}}
          ],"clarificationQuestion":null,"message":"Creé Cliente y su relación."}
          """;
        GeminiUmlInterpreter interpreter = configured(modelJson);
        var current = Map.<String,Object>of("classes", List.of(Map.of("id","pedido","name","Pedido","attributes",List.of())), "relations", List.of());
        var result = interpreter.interpret(new UmlAiCommandRequest("Crea Cliente y relaciónalo con Pedido", current));

        assertThat(result.getInterpreter()).isEqualTo("GEMINI");
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
        server.createContext("/models/gemini-3.8-flash:generateContent", exchange -> {
            body[0] = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String structured="{\"status\":\"CLARIFICATION\",\"operations\":[],\"clarificationQuestion\":\"¿Qué deseas crear?\",\"message\":\"Necesito más información.\"}";
            byte[] response=mapper.writeValueAsBytes(Map.of("candidates",List.of(Map.of("content",Map.of("parts",List.of(Map.of("text",structured)))))));
            exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });server.start();
        GeminiUmlInterpreter interpreter = interpreter();
        interpreter.interpret(new UmlAiCommandRequest("algo", Map.of("classes",List.of(),"relations",List.of())));
        assertThat(body[0]).doesNotContain("test-secret-key");
        var request = mapper.readTree(body[0]);
        var schema = request.path("generationConfig").path("responseJsonSchema");
        assertThat(request.path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(schema.path("properties").path("status").path("enum").toString())
                .isEqualTo("[\"READY\",\"CLARIFICATION\",\"ERROR\"]");
        assertThat(schema.path("properties").path("operations").path("items").path("properties")
                .path("type").path("enum")).hasSize(9);
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

    @Test void reintentaErroresTransitoriosHastaObtenerRespuestaValida() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        String structured="""
          {"status":"READY","operations":[{"type":"CLASS_CREATE","target":{"className":"Cliente","attributeName":null,"sourceClassName":null,"targetClassName":null},"parameters":{}}],"clarificationQuestion":null,"message":"Clase creada."}
          """;
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/models/gemini-3.8-flash:generateContent", exchange -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 4) {
                exchange.sendResponseHeaders(503, -1);
            } else {
                byte[] response=mapper.writeValueAsBytes(Map.of("candidates",List.of(Map.of("content",Map.of("parts",List.of(Map.of("text",structured)))))));
                exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);
            }
            exchange.close();
        });
        server.start();

        var result = interpreter().interpret(new UmlAiCommandRequest("Crea Cliente", Map.of("classes",List.of(),"relations",List.of())));

        assertThat(attempts).hasValue(4);
        assertThat(result.getInterpreter()).isEqualTo("GEMINI");
        assertThat(result.isFallbackUsed()).isFalse();
    }

    @Test void noReintentaErroresNoTransitorios() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/models/gemini-3.8-flash:generateContent", exchange -> {
            attempts.incrementAndGet();
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
        });
        server.start();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> interpreter().interpret(new UmlAiCommandRequest("Crea Cliente", Map.of("classes",List.of(),"relations",List.of()))));
        assertThat(attempts).hasValue(1);
    }

    @Test void respetaRetryAfterEnRespuesta429() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        String structured="""
          {"status":"READY","operations":[{"type":"CLASS_CREATE","target":{"className":"Cliente","attributeName":null,"sourceClassName":null,"targetClassName":null},"parameters":{}}],"clarificationQuestion":null,"message":"Clase creada."}
          """;
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/models/gemini-3.8-flash:generateContent", exchange -> {
            if (attempts.incrementAndGet() == 1) {
                exchange.getResponseHeaders().add("Retry-After", "0");
                exchange.sendResponseHeaders(429, -1);
            } else {
                byte[] response=mapper.writeValueAsBytes(Map.of("candidates",List.of(Map.of("content",Map.of("parts",List.of(Map.of("text",structured)))))));
                exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);
            }
            exchange.close();
        });
        server.start();
        GeminiUmlInterpreter value=interpreter();
        ReflectionTestUtils.setField(value,"retryBaseMs",5_000L);
        long started=System.nanoTime();

        var result=value.interpret(new UmlAiCommandRequest("Crea Cliente", Map.of("classes",List.of(),"relations",List.of())));

        assertThat(attempts).hasValue(2);
        assertThat(Duration.ofNanos(System.nanoTime()-started).toMillis()).isLessThan(1_000L);
        assertThat(result.getInterpreter()).isEqualTo("GEMINI");
    }

    @Test void validaReconocimientoVisualSinInventarReferencias() {
        GeminiUmlInterpreter value=new GeminiUmlInterpreter(mapper);
        ReflectionTestUtils.setField(value,"model","gemini-3.8-flash");
        Map<String,Object> raw=Map.of(
                "status","READY","message","Diagrama reconocido.","warnings",List.of("Revisar texto pequeño."),
                "classes",List.of(
                        Map.of("name","Usuario","attributes",List.of(Map.of("name","id","type","Long","isPrimaryKey",true),Map.of("name","correo","type","String","isPrimaryKey",false))),
                        Map.of("name","Pedido","attributes",List.of(Map.of("name","fecha","type","Date","isPrimaryKey",false)))),
                "relations",List.of(
                        Map.of("sourceClassName","Usuario","targetClassName","Pedido","type","association","label","realiza","sourceMultiplicity","1","targetMultiplicity","0..*"),
                        Map.of("sourceClassName","Fantasma","targetClassName","Pedido","type","association","label","","sourceMultiplicity","1","targetMultiplicity","muchos")));

        Map<String,Object> result=value.validateImage(raw);

        assertThat(result).containsEntry("status","READY").containsEntry("provider","GEMINI");
        assertThat((List<?>)result.get("classes")).hasSize(2);
        assertThat((List<?>)result.get("relations")).hasSize(1);
        assertThat((List<?>)result.get("warnings")).hasSize(2);
    }

    @Test void rechazaImagenSinClasesLegibles() {
        GeminiUmlInterpreter value=new GeminiUmlInterpreter(mapper);
        ReflectionTestUtils.setField(value,"model","gemini-3.8-flash");
        Map<String,Object> result=value.validateImage(Map.of("status","READY","message","","warnings",List.of(),"classes",List.of(),"relations",List.of()));
        assertThat(result).containsEntry("status","ERROR");
    }

    private GeminiUmlInterpreter configured(String structured) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/models/gemini-3.8-flash:generateContent", exchange -> {
            byte[] response=mapper.writeValueAsBytes(Map.of("candidates",List.of(Map.of("content",Map.of("parts",List.of(Map.of("text",structured)))))));
            exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });server.start();return interpreter();
    }
    private GeminiUmlInterpreter interpreter() {
        GeminiUmlInterpreter value=new GeminiUmlInterpreter(mapper);
        ReflectionTestUtils.setField(value,"apiKey","test-secret-key");
        ReflectionTestUtils.setField(value,"model","gemini-3.8-flash");
        ReflectionTestUtils.setField(value,"baseUrl","http://127.0.0.1:"+server.getAddress().getPort());
        ReflectionTestUtils.setField(value,"timeoutSeconds",5L);
        ReflectionTestUtils.setField(value,"retryBaseMs",1L);
        return value;
    }
}
