package com.arqnova.servicios;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class UmlAiProviderPipelineTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private HttpServer server;
    private final AtomicInteger geminiCalls=new AtomicInteger(),groqCalls=new AtomicInteger();
    private int geminiStatus=200,groqStatus=200,rejectedKeys=0;
    private String geminiJson="{\"ok\":true}",groqJson="{\"ok\":true}";
    private String groqReviewJson,geminiReviewJson;
    private Map<?,?> geminiPayload,groqPayload;
    private int reviewQuotaAttempts;
    private String reviewRetryAfter="0.001";
    private boolean rejectLargeOutput;
    private int quotaKeys;
    private String groqError="unavailable";
    private final List<String> keysSeen=new ArrayList<>();
    private UmlAiProviderPipeline pipeline;
    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            boolean isGemini=exchange.getRequestURI().getPath().contains("generateContent");
            int call=(isGemini?geminiCalls:groqCalls).incrementAndGet();
            var payload=mapper.readValue(exchange.getRequestBody(),Map.class);
            if(isGemini){geminiPayload=payload;keysSeen.add(exchange.getRequestHeaders().getFirst("x-goog-api-key"));}else groqPayload=payload;
            int status=isGemini?(call<=rejectedKeys?401:keysSeen.size()<=quotaKeys?429:geminiStatus):groqStatus;
            if(!isGemini && rejectLargeOutput && ((Number)payload.get("max_completion_tokens")).intValue()>768)status=429;
            if(!isGemini&&call<=reviewQuotaAttempts){status=429;exchange.getResponseHeaders().add("Retry-After",reviewRetryAfter);}
            String body=status==200?mapper.writeValueAsString(isGemini?
                Map.of("candidates",List.of(Map.of("finishReason","STOP","content",Map.of("parts",List.of(Map.of("text",geminiReviewJson!=null&&((Number)((Map<?,?>)payload.get("generationConfig")).get("maxOutputTokens")).intValue()<8192?geminiReviewJson:geminiJson)))))):
                Map.of("choices",List.of(Map.of("finish_reason","stop","message",Map.of("content",groqReviewJson!=null&&((Number)payload.get("max_completion_tokens")).intValue()<=512?groqReviewJson:groqJson))))):mapper.writeValueAsString(Map.of("error",Map.of("message",isGemini?"unavailable":groqError)));
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        pipeline=new UmlAiProviderPipeline(mapper);
        String base="http://127.0.0.1:"+server.getAddress().getPort();
        set("geminiBaseUrl",base);set("groqBaseUrl",base);set("geminiKey1","test-key-1");set("geminiKey2","test-key-2");set("geminiKey3","test-key-3");set("groqKey","test-groq");
    }
    private void set(String name,Object value){ReflectionTestUtils.setField(pipeline,name,value);}
    @AfterEach void stop(){server.stop(0);}
    private UmlAiProviderPipeline.Result run() throws Exception {return pipeline.generate("Genera UML",Map.of("type","object"),null);}
    @Test void usaPrimeroGeminiSinConsultarGroq() throws Exception {
        var result=run();assertThat(result.provider()).isEqualTo("GEMINI");assertThat(result.fallbackUsed()).isFalse();assertThat(groqCalls.get()).isZero();
        assertThat(((Map<?,?>)geminiPayload.get("generationConfig")).get("responseJsonSchema")).isNotNull();
        assertThat(mapper.writeValueAsString(geminiPayload)).contains("thinkingLevel","low");
    }
    @Test void rotaCredencialesRechazadasHastaLaTerceraYRecuerdaLasInvalidas() throws Exception {
        rejectedKeys=2;var result=run();assertThat(result.provider()).isEqualTo("GEMINI");assertThat(result.fallbackUsed()).isTrue();
        assertThat(keysSeen).containsExactly("test-key-1","test-key-2","test-key-3");run();assertThat(keysSeen).hasSize(4);assertThat(keysSeen.get(3)).isEqualTo("test-key-3");
    }
    @Test void cuotasIndependientesPruebanTresClavesYRespetanEnfriamientos() throws Exception {
        geminiStatus=429;assertThat(run().provider()).isEqualTo("GROQ");assertThat(geminiCalls.get()).isEqualTo(3);assertThat(run().fallbackUsed()).isTrue();assertThat(geminiCalls.get()).isEqualTo(3);assertThat(groqCalls.get()).isEqualTo(2);
    }
    @Test void tresCredencialesRechazadasPasanAGroqSinBucle() throws Exception {
        geminiStatus=403;assertThat(run().provider()).isEqualTo("GROQ");assertThat(geminiCalls.get()).isEqualTo(3);assertThat(groqCalls.get()).isEqualTo(1);
    }
    @Test void caidaDeGeminiUsaGroqUnaSolaVez() throws Exception {
        geminiStatus=503;assertThat(run().provider()).isEqualTo("GROQ");assertThat(geminiCalls.get()).isEqualTo(3);assertThat(groqCalls.get()).isEqualTo(1);
    }
    @Test void jsonInvalidoNoLlegaAlEditorYUsaRespaldo() throws Exception {
        geminiJson="respuesta no JSON";assertThat(run().provider()).isEqualTo("GROQ");
    }
    @Test void errorDeAmbosProveedoresNoHaceReintentosInfinitos() {
        geminiStatus=429;groqStatus=429;
        assertThatThrownBy(()->run()).isInstanceOf(OpenAiUmlInterpreter.ProviderException.class);
        assertThatThrownBy(()->run()).isInstanceOf(OpenAiUmlInterpreter.ProviderException.class);
        assertThat(geminiCalls.get()).isEqualTo(3);assertThat(groqCalls.get()).isEqualTo(1);
    }
    @Test void imagenUsaModeloVisualEnGroqYConservaComposicionValidada() throws Exception {
        geminiStatus=429;
        groqReviewJson="{\"status\":\"READY\",\"relations\":[[\"Pedido\",\"Detalle\",\"composition\",\"contiene\",\"1\",\"1..*\",\"\"]]}";
        groqJson="""
          {"status":"READY","message":"ok","warnings":[],"classes":[{"name":"Pedido","attributes":[],"methods":[],"x":0.1,"y":0.1},{"name":"Detalle","attributes":[],"methods":[],"x":0.4,"y":0.5}],"relations":[{"sourceClassName":"Pedido","targetClassName":"Detalle","type":"composition","label":"contiene","sourceMultiplicity":"1","targetMultiplicity":"1..*","associationClassName":""}]}
          """;
        var interpreter=new OpenAiUmlInterpreter(mapper);ReflectionTestUtils.setField(interpreter,"providerPipeline",pipeline);
        var result=interpreter.analyzeImage(new MockMultipartFile("image","uml.png","image/png",new byte[]{1,2,3}));
        assertThat(result).containsEntry("provider","GROQ").containsEntry("model","qwen/qwen3.8-27b").containsEntry("fallbackUsed",true);
        assertThat((List<?>)result.get("relations")).hasSize(1);assertThat(groqPayload.get("model")).isEqualTo("qwen/qwen3.8-27b");
        var relation=(Map<?,?>)((List<?>)result.get("relations")).get(0);assertThat(relation.get("type")).isEqualTo("composition");assertThat(relation.get("sourceClassName")).isEqualTo("Pedido");
        assertThat(mapper.writeValueAsString(geminiPayload)).contains("inlineData","AQID");
        assertThat(mapper.writeValueAsString(groqPayload)).contains("image_url","data:image/png;base64,AQID");
    }
    @Test void textoDeRespaldoMantieneValidacionYProveedorReal() throws Exception {
        geminiStatus=429;groqJson="""
            {"status":"READY","message":"ok","operations":[{"type":"CLASS_CREATE","target":{"className":"Libro"},"parameters":{"name":"Libro","attributes":[]}}]}
            """;
        var interpreter=new OpenAiUmlInterpreter(mapper);ReflectionTestUtils.setField(interpreter,"providerPipeline",pipeline);
        var request=new com.arqnova.modelo.dto.request.UmlAiCommandRequest();request.setInstruction("Crea Libro");request.setModel(Map.of("classes",List.of(),"relations",List.of()));
        var result=interpreter.interpret(request);assertThat(result.getStatus()).isEqualTo("READY");assertThat(result.getInterpreter()).isEqualTo("GROQ");assertThat(result.isFallbackUsed()).isTrue();assertThat(result.getOperations()).hasSize(1);
    }
    @Test void presupuestoVisualRechazadoSeReduceUnaSolaVez() throws Exception {
        geminiStatus=503;rejectLargeOutput=true;groqError="output tokens per minute";
        assertThat(pipeline.generate("Imagen",Map.of("type","object"),"data:image/png;base64,AQID").provider()).isEqualTo("GROQ");
        assertThat(groqCalls.get()).isEqualTo(2);assertThat(groqPayload.get("max_completion_tokens")).isEqualTo(768);
    }
    @Test void cuotaDeSalidaAgotadaNoGeneraUnBucleDeReintentos() {
        geminiStatus=503;groqStatus=429;groqError="output tokens per minute";
        assertThatThrownBy(()->pipeline.generate("Imagen",Map.of("type","object"),"data:image/png;base64,AQID")).isInstanceOf(OpenAiUmlInterpreter.ProviderException.class);
        assertThat(groqCalls.get()).isEqualTo(2);
    }

    @Test void primeraCuotaAgotadaUsaSegundaYNoConsultaGroq() throws Exception {
        quotaKeys=1;var result=run();assertThat(result.provider()).isEqualTo("GEMINI");assertThat(result.fallbackUsed()).isTrue();assertThat(keysSeen).containsExactly("test-key-1","test-key-2");assertThat(groqCalls.get()).isZero();
        run();assertThat(keysSeen).containsExactly("test-key-1","test-key-2","test-key-2");
    }
    @Test void dosCuotasAgotadasUsanTerceraAntesDeGroq() throws Exception {
        quotaKeys=2;assertThat(run().provider()).isEqualTo("GEMINI");assertThat(keysSeen).containsExactly("test-key-1","test-key-2","test-key-3");assertThat(groqCalls.get()).isZero();
    }
    @Test void cuotaRestablecidaVuelveAPriorizarPrimeraClave() throws Exception {
        quotaKeys=1;run();((Map<?,?>)ReflectionTestUtils.getField(pipeline,"geminiKeysUnavailableUntil")).clear();quotaKeys=0;
        assertThat(run().provider()).isEqualTo("GEMINI");assertThat(keysSeen.get(keysSeen.size()-1)).isEqualTo("test-key-1");
    }

    @Test void revisionVisualRespetaPausaCortaYReintentaSoloUnaVez() throws Exception {
        reviewQuotaAttempts=1;groqError="input tokens per minute";
        assertThat(pipeline.reviewImageGraph("Verifica",Map.of("type","object"),"data:image/png;base64,AQID",128)).contains("ok");assertThat(groqCalls.get()).isEqualTo(2);
    }
    @Test void revisionVisualNoReintentaIndefinidamenteUnaCuotaAgotada() {
        reviewQuotaAttempts=3;groqError="input tokens per minute";
        assertThatThrownBy(()->pipeline.reviewImageGraph("Verifica",Map.of("type","object"),"data:image/png;base64,AQID",128)).isInstanceOf(OpenAiUmlInterpreter.ProviderException.class);assertThat(groqCalls.get()).isEqualTo(2);
    }
    @Test void revisionVisualNoBloqueaLaSolicitudCuandoLaPausaEsLarga() {
        reviewQuotaAttempts=3;reviewRetryAfter="20";
        assertThatThrownBy(()->pipeline.reviewImageGraph("Verifica",Map.of("type","object"),"data:image/png;base64,AQID",128)).isInstanceOf(OpenAiUmlInterpreter.ProviderException.class);assertThat(groqCalls.get()).isEqualTo(1);
    }
    @Test void conexionesNoConfirmadasNoSeDevuelvenComoDiagramaListo() throws Exception {
        geminiStatus=429;groqJson="{\"status\":\"READY\",\"message\":\"ok\",\"warnings\":[],\"classes\":[{\"name\":\"Solo\",\"attributes\":[],\"methods\":[],\"x\":0.1,\"y\":0.1}],\"relations\":[]}";
        groqReviewJson="{\"status\":\"UNCLEAR\",\"relations\":[]}";
        var interpreter=new OpenAiUmlInterpreter(mapper);ReflectionTestUtils.setField(interpreter,"providerPipeline",pipeline);
        assertThat(interpreter.analyzeImage(new MockMultipartFile("image","foto.png","image/png",new byte[]{1,2,3}))).containsEntry("status","ERROR");
    }

    @Test void geminiRevisaClaseDeAsociacionEnLugarDeAceptarDosAsociacionesInventadas() throws Exception {
        geminiJson="""
          {"status":"READY","message":"ok","warnings":[],"classes":[{"name":"Curso","attributes":[],"methods":[],"x":0.1,"y":0.1},{"name":"Alumno","attributes":[],"methods":[],"x":0.8,"y":0.1},{"name":"Inscripcion","attributes":[{"name":"nota","type":null,"isPrimaryKey":false}],"methods":[],"x":0.5,"y":0.6}],"relations":[{"sourceClassName":"Curso","targetClassName":"Alumno","type":"association","label":"","sourceMultiplicity":"*","targetMultiplicity":"1..*","associationClassName":""},{"sourceClassName":"Curso","targetClassName":"Inscripcion","type":"association","label":"","sourceMultiplicity":"1","targetMultiplicity":"*","associationClassName":""},{"sourceClassName":"Alumno","targetClassName":"Inscripcion","type":"association","label":"","sourceMultiplicity":"1","targetMultiplicity":"*","associationClassName":""}]}
          """;
        geminiReviewJson="""
          {"status":"READY","relations":[["Curso","Alumno","association","","*","1..*","Inscripcion"]]}
          """;
        var interpreter=new OpenAiUmlInterpreter(mapper);ReflectionTestUtils.setField(interpreter,"providerPipeline",pipeline);
        var result=interpreter.analyzeImage(new MockMultipartFile("image","foto.png","image/png",new byte[]{1,2,3}));
        assertThat(result).containsEntry("status","READY").containsEntry("provider","GEMINI");
        assertThat(geminiCalls.get()).isEqualTo(2);assertThat(groqCalls.get()).isZero();
        assertThat((List<?>)result.get("classes")).hasSize(3);
        assertThat((List<?>)result.get("relations")).hasSize(1);
        assertThat((Map<String,Object>)((List<?>)result.get("relations")).get(0)).containsEntry("associationClassName","Inscripcion").containsEntry("sourceMultiplicity","*").containsEntry("targetMultiplicity","1..*");
    }
    @Test void geminiConConexionesIlegiblesNoDevuelveUnGrafoListo() throws Exception {
        geminiJson="{\"status\":\"READY\",\"message\":\"ok\",\"warnings\":[],\"classes\":[{\"name\":\"Entidad\",\"attributes\":[],\"methods\":[],\"x\":0.1,\"y\":0.1}],\"relations\":[]}";
        geminiReviewJson="{\"status\":\"UNCLEAR\",\"relations\":[]}";
        var interpreter=new OpenAiUmlInterpreter(mapper);ReflectionTestUtils.setField(interpreter,"providerPipeline",pipeline);
        assertThat(interpreter.analyzeImage(new MockMultipartFile("image","foto.png","image/png",new byte[]{1,2,3}))).containsEntry("status","ERROR");
        assertThat(geminiCalls.get()).isEqualTo(2);assertThat(groqCalls.get()).isZero();
    }
    @Test void revisionGeminiRotaClavesSinDependerDeLaCuotaGroq() throws Exception {
        quotaKeys=2;
        assertThat(pipeline.reviewImageGraph(new UmlAiProviderPipeline.Result("{}","GEMINI","test",false),"Revisa",Map.of("type","object"),"data:image/png;base64,AQID",1024)).contains("ok");
        assertThat(keysSeen).containsExactly("test-key-1","test-key-2","test-key-3");assertThat(groqCalls.get()).isZero();
    }

    @Test void visionNoRepiteElSchemaExtensoPeroMantieneElFormatoYLaImagen() throws Exception {
        geminiStatus=503;
        pipeline.generate("Transcribe las clases",Map.of("type","object","description","SCHEMA_DUPLICADO_QUE_NO_SE_ENVIA_EN_VISION"),"data:image/png;base64,AQID");
        assertThat(mapper.writeValueAsString(groqPayload)).doesNotContain("SCHEMA_DUPLICADO_QUE_NO_SE_ENVIA_EN_VISION").contains("image_url","Transcribe las clases");
    }

}
