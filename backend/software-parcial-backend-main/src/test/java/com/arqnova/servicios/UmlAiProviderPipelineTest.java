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
    private Map<?,?> geminiPayload,groqPayload;
    private final List<String> keysSeen=new ArrayList<>();
    private UmlAiProviderPipeline pipeline;
    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            boolean isGemini=exchange.getRequestURI().getPath().contains("generateContent");
            int call=(isGemini?geminiCalls:groqCalls).incrementAndGet();
            var payload=mapper.readValue(exchange.getRequestBody(),Map.class);
            if(isGemini){geminiPayload=payload;keysSeen.add(exchange.getRequestHeaders().getFirst("x-goog-api-key"));}else groqPayload=payload;
            int status=isGemini?(call<=rejectedKeys?401:geminiStatus):groqStatus;
            String body=status==200?mapper.writeValueAsString(isGemini?
                Map.of("candidates",List.of(Map.of("finishReason","STOP","content",Map.of("parts",List.of(Map.of("text",geminiJson)))))):
                Map.of("choices",List.of(Map.of("finish_reason","stop","message",Map.of("content",groqJson))))):"{\"error\":{\"message\":\"unavailable\"}}";
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
    @Test void cuotaCompartidaSaltaLasOtrasDosClavesYRespetaEnfriamiento() throws Exception {
        geminiStatus=429;assertThat(run().provider()).isEqualTo("GROQ");assertThat(geminiCalls.get()).isEqualTo(1);assertThat(run().fallbackUsed()).isTrue();assertThat(geminiCalls.get()).isEqualTo(1);assertThat(groqCalls.get()).isEqualTo(2);
    }
    @Test void tresCredencialesRechazadasPasanAGroqSinBucle() throws Exception {
        geminiStatus=403;assertThat(run().provider()).isEqualTo("GROQ");assertThat(geminiCalls.get()).isEqualTo(3);assertThat(groqCalls.get()).isEqualTo(1);
    }
    @Test void caidaDeGeminiUsaGroqUnaSolaVez() throws Exception {
        geminiStatus=503;assertThat(run().provider()).isEqualTo("GROQ");assertThat(geminiCalls.get()).isEqualTo(1);assertThat(groqCalls.get()).isEqualTo(1);
    }
    @Test void jsonInvalidoNoLlegaAlEditorYUsaRespaldo() throws Exception {
        geminiJson="respuesta no JSON";assertThat(run().provider()).isEqualTo("GROQ");
    }
    @Test void errorDeAmbosProveedoresNoHaceReintentosInfinitos() {
        geminiStatus=429;groqStatus=429;
        assertThatThrownBy(()->run()).isInstanceOf(OpenAiUmlInterpreter.ProviderException.class);
        assertThatThrownBy(()->run()).isInstanceOf(OpenAiUmlInterpreter.ProviderException.class);
        assertThat(geminiCalls.get()).isEqualTo(1);assertThat(groqCalls.get()).isEqualTo(1);
    }
    @Test void imagenUsaModeloVisualEnGroqYConservaComposicionValidada() throws Exception {
        geminiStatus=429;
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
}
