package com.arqnova.servicios;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Provider transport only. The interpreter still validates every UML operation and image result. */
@Slf4j
@Service
public class UmlAiProviderPipeline {
    @Value("${GEMINI_API_KEY_1:}") private String geminiKey1="";
    @Value("${GEMINI_API_KEY_2:}") private String geminiKey2="";
    @Value("${GEMINI_API_KEY_3:}") private String geminiKey3="";
    @Value("${GEMINI_MODEL:gemini-3.8-flash}") private String geminiModel="gemini-3.8-flash";
    @Value("${GEMINI_BASE_URL:https://generativelanguage.googleapis.com/v1beta}") private String geminiBaseUrl="https://generativelanguage.googleapis.com/v1beta";
    @Value("${GROQ_API_KEY:}") private String groqKey="";
    @Value("${GROQ_MODEL:openai/gpt-oss-120b}") private String groqModel="openai/gpt-oss-120b";
    @Value("${GROQ_IMAGE_MODEL:qwen/qwen3.8-27b}") private String groqImageModel="qwen/qwen3.8-27b";
    @Value("${GROQ_BASE_URL:https://api.groq.com/openai/v1}") private String groqBaseUrl="https://api.groq.com/openai/v1";
    @Value("${AI_PROVIDER_TIMEOUT_SECONDS:22}") private int timeoutSeconds=22;
    @Value("${GROQ_IMAGE_MAX_TOKENS:8192}") private int groqImageMaxTokens=8192;
    private final ObjectMapper mapper;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    // Each Gemini credential belongs to an independent account/project.
    private final AtomicLong groqUnavailableUntil=new AtomicLong();
    private final Map<Integer,Long> geminiKeysUnavailableUntil=new ConcurrentHashMap<>();
    public record Result(String json,String provider,String model,boolean fallbackUsed) {}
    public UmlAiProviderPipeline(ObjectMapper mapper){this.mapper=mapper;}
    public boolean isConfigured(){return !geminiKeys().isEmpty()||!groqKey.isBlank();}
    private List<String> geminiKeys(){return java.util.stream.Stream.of(geminiKey1,geminiKey2,geminiKey3).map(String::trim).filter(k->!k.isEmpty()).distinct().toList();}

    public Result generate(String prompt,Map<String,Object> schema,String imageUrl) throws Exception {
        List<String> keys=geminiKeys();
        int attempts=0,lastStatus=keys.isEmpty()?503:429;
        for(int i=0;i<keys.size();i++){
            if(System.currentTimeMillis()<geminiKeysUnavailableUntil.getOrDefault(i,0L))continue;
            attempts++;
            try{
                String json=gemini(prompt,schema,imageUrl,keys.get(i));
                return new Result(json,"GEMINI",geminiModel,i>0||attempts>1);
            }catch(InterruptedException e){Thread.currentThread().interrupt();throw e;
            }catch(UpstreamFailure e){
                lastStatus=e.status;
                log.warn("UML provider failed: provider=GEMINI credential={} status={}",i+1,e.status);
                if(e.status==401||e.status==403){geminiKeysUnavailableUntil.put(i,System.currentTimeMillis()+300_000);continue;}
                geminiKeysUnavailableUntil.put(i,System.currentTimeMillis()+e.cooldownMillis);
                continue;
            }catch(java.io.IOException e){
                log.warn("UML provider failed: provider=GEMINI type={}",e.getClass().getSimpleName());
                geminiKeysUnavailableUntil.put(i,System.currentTimeMillis()+30_000);continue;
            }
        }
        if(!groqKey.isBlank()&&System.currentTimeMillis()>=groqUnavailableUntil.get()){
            try{
                String selectedModel=imageUrl==null?groqModel:groqImageModel;
                String json=groq(prompt,schema,imageUrl,selectedModel);
                return new Result(json,"GROQ",selectedModel,!keys.isEmpty());
            }catch(InterruptedException e){Thread.currentThread().interrupt();throw e;
            }catch(UpstreamFailure e){
                log.warn("UML provider failed: provider=GROQ status={}",e.status);
                lastStatus=e.status;groqUnavailableUntil.set(System.currentTimeMillis()+e.cooldownMillis);
            }catch(java.io.IOException e){
                log.warn("UML provider failed: provider=GROQ type={}",e.getClass().getSimpleName());
                lastStatus=503;groqUnavailableUntil.set(System.currentTimeMillis()+30_000);
            }
        }
        throw new OpenAiUmlInterpreter.ProviderException(lastStatus);
    }

    private String gemini(String prompt,Map<String,Object> schema,String imageUrl,String key) throws Exception {
        return gemini(prompt,schema,imageUrl,key,8192);
    }

    private String gemini(String prompt,Map<String,Object> schema,String imageUrl,String key,int maxTokens) throws Exception {
        List<Map<String,Object>> parts=new ArrayList<>();parts.add(Map.of("text",prompt));
        if(imageUrl!=null){int marker=imageUrl.indexOf(";base64,");if(marker<0)throw new IllegalArgumentException("Imagen inválida");parts.add(Map.of("inlineData",Map.of("mimeType",imageUrl.substring(5,marker),"data",imageUrl.substring(marker+8))));}
        Map<String,Object> generation=new LinkedHashMap<>();
        if(!geminiModel.startsWith("gemini-3"))generation.put("temperature",0);generation.put("maxOutputTokens",maxTokens);
        generation.put("responseMimeType","application/json");generation.put("responseJsonSchema",schema);
        if(geminiModel.startsWith("gemini-2.5-flash"))generation.put("thinkingConfig",Map.of("thinkingBudget",0));
        else if(geminiModel.startsWith("gemini-3"))generation.put("thinkingConfig",Map.of("thinkingLevel","low"));
        JsonNode data=post(geminiBaseUrl+"/models/"+geminiModel+":generateContent",Map.of("x-goog-api-key",key),Map.of("contents",List.of(Map.of("role","user","parts",parts)),"generationConfig",generation));
        JsonNode candidate=data.path("candidates").path(0);
        if(!"STOP".equals(candidate.path("finishReason").asText()))throw new UpstreamFailure(502,60_000);
        StringBuilder text=new StringBuilder();
        for(JsonNode part:candidate.path("content").path("parts"))if(!part.path("thought").asBoolean(false))text.append(part.path("text").asText(""));
        return jsonObject(text.toString());
    }

    /** Apply the same graph review to Gemini and Groq; never accept an unreviewed fallback. */
    public String reviewImageGraph(Result initial,String prompt,Map<String,Object> schema,String imageUrl,int maxTokens) throws Exception {
        if(initial==null||!"GEMINI".equals(initial.provider()))return reviewImageGraph(prompt,schema,imageUrl,maxTokens);
        List<String> keys=geminiKeys();
        int budget=Math.max(2048,Math.min(4096,maxTokens));
        for(int i=0;i<keys.size();i++) {
            if(System.currentTimeMillis()<geminiKeysUnavailableUntil.getOrDefault(i,0L))continue;
            try { return gemini(prompt,schema,imageUrl,keys.get(i),budget); }
            catch(InterruptedException error){Thread.currentThread().interrupt();throw error;}
            catch(UpstreamFailure error){
                log.warn("UML graph review failed: provider=GEMINI credential={} status={}",i+1,error.status);
                geminiKeysUnavailableUntil.put(i,System.currentTimeMillis()+error.cooldownMillis);
            } catch(java.io.IOException error){geminiKeysUnavailableUntil.put(i,System.currentTimeMillis()+30_000);}
        }
        if(groqKey.isBlank()||System.currentTimeMillis()<groqUnavailableUntil.get())throw new OpenAiUmlInterpreter.ProviderException(503);
        return reviewImageGraph(prompt,schema,imageUrl,maxTokens);
    }

    public String reviewImageGraph(String prompt,Map<String,Object> schema,String imageUrl,int maxTokens) throws Exception {
        int budget=Math.max(64,Math.min(4096,maxTokens));
        try{return groq(prompt,schema,imageUrl,groqImageModel,budget);}
        catch(UpstreamFailure error){
            if(error.status==429&&error.retryAfterMillis>0&&error.retryAfterMillis<=10_000){
                Thread.sleep(error.retryAfterMillis+100);
                try{return groq(prompt,schema,imageUrl,groqImageModel,budget);}
                catch(UpstreamFailure retryError){throw new OpenAiUmlInterpreter.ProviderException(retryError.status);}
            }throw new OpenAiUmlInterpreter.ProviderException(error.status);
        }
    }

    private String groq(String prompt,Map<String,Object> schema,String imageUrl,String selectedModel) throws Exception {
        int max=imageUrl==null?8192:Math.max(256,groqImageMaxTokens);
        try{return groq(prompt,schema,imageUrl,selectedModel,max);}
        catch(UpstreamFailure error){
            // A smaller image response can fit a restricted output allowance; never rotate keys for quota.
            if(imageUrl!=null && max>768 && error.status==429 && "OUTPUT_TOKENS".equals(error.quota))
                return groq(prompt,schema,imageUrl,selectedModel,768);
            throw error;
        }
    }

    private String groq(String prompt,Map<String,Object> schema,String imageUrl,String selectedModel,int maxTokens) throws Exception {
        boolean compact=imageUrl!=null && ((Map<?,?>)schema.getOrDefault("properties",Map.of())).containsKey("classes");
        if(compact){schema=tupleImageSchema();prompt+="\nFORMATO DE SALIDA OBLIGATORIO: classes contiene arrays [nombre, atributos, métodos, x, y]. Cada atributo es [nombre,tipo,esPK] y cada método [nombre,parámetros,tipoRetorno]. relations contiene arrays [origen,destino,tipo,etiqueta,multiplicidadOrigen,multiplicidadDestino,claseAsociación]. No uses objetos dentro de classes ni relations. Ejemplo: {\"status\":\"READY\",\"classes\":[[\"Ejemplo\",[[\"dato\",null,false]],[],0.1,0.2]],\"relations\":[],\"warnings\":[],\"message\":\"ok\"}. En composición el origen es la clase junto al rombo. Una clase unida con línea discontinua a la asociación se indica únicamente en la séptima posición de esa asociación, conservando el vínculo original y sus multiplicidades; no se sustituye por dos relaciones nuevas. Conserva los nombres originales en su idioma, sin traducción. JSON compacto sin sangría.";}

        List<Map<String,Object>> content=new ArrayList<>();
        content.add(Map.of("type","text","text",prompt+(imageUrl==null?"\nDevuelve exclusivamente un objeto JSON con este JSON Schema: "+mapper.writeValueAsString(schema):"\nDevuelve JSON válido conforme al formato indicado, sin comentarios.")));
        if(imageUrl!=null)content.add(Map.of("type","image_url","image_url",Map.of("url",imageUrl)));
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("model",selectedModel);payload.put("temperature",0);payload.put("max_completion_tokens",maxTokens);
        payload.put("messages",List.of(Map.of("role","user","content",content)));
        if(imageUrl==null&&selectedModel.startsWith("openai/gpt-oss-")){
            payload.put("reasoning_effort","low");
            payload.put("response_format",Map.of("type","json_schema","json_schema",Map.of("name","uml_command","strict",true,"schema",schema)));
        }else payload.put("response_format",Map.of("type","json_object"));
        JsonNode data=post(groqBaseUrl+"/chat/completions",Map.of("Authorization","Bearer "+groqKey),payload);
        if(imageUrl!=null)log.info("UML vision usage: promptTokens={} completionTokens={} maxTokens={}",data.path("usage").path("prompt_tokens").asInt(-1),data.path("usage").path("completion_tokens").asInt(-1),maxTokens);
        JsonNode choice=data.path("choices").path(0);
        if(!"stop".equals(choice.path("finish_reason").asText()))throw new UpstreamFailure(502,60_000);
        String json=jsonObject(choice.path("message").path("content").asText(""));
        return compact?mapper.writeValueAsString(expandTupleImage(mapper.readValue(json,Map.class))):json;
    }

    private static Map<String,Object> tupleImageSchema() {
        Map<String,Object> string=Map.of("type","string"),nullable=Map.of("type",List.of("string","null"));
        Map<String,Object> attribute=tuple(List.of(string,nullable,Map.of("type","boolean")));
        Map<String,Object> method=tuple(List.of(string,string,nullable));
        Map<String,Object> coordinate=Map.of("type",List.of("number","null"));
        Map<String,Object> clazz=tuple(List.of(string,Map.of("type","array","items",attribute),Map.of("type","array","items",method),coordinate,coordinate));
        Map<String,Object> relation=tuple(List.of(string,string,string,string,string,string,string));
        return Map.of("type","object","properties",Map.of("status",string,"classes",Map.of("type","array","items",clazz),"relations",Map.of("type","array","items",relation),"warnings",Map.of("type","array","items",string),"message",string),"required",List.of("status","classes","relations","warnings","message"));
    }
    private static Map<String,Object> tuple(List<Map<String,Object>> fields){return Map.of("type","array","prefixItems",fields,"minItems",fields.size(),"maxItems",fields.size());}

    @SuppressWarnings("unchecked")
    static Map<String,Object> expandTupleImage(Map<String,Object> value) {
        Map<String,Object> result=new LinkedHashMap<>(value);
        if(value.get("classes") instanceof List<?> classes) {
            List<Object> expanded=new ArrayList<>();
            for(Object item:classes){
                if(!(item instanceof List<?> c)||c.size()!=5){expanded.add(item);continue;}
                var clazz=new LinkedHashMap<String,Object>();clazz.put("name",c.get(0));clazz.put("attributes",expandMembers(c.get(1),List.of("name","type","isPrimaryKey")));clazz.put("methods",expandMembers(c.get(2),List.of("name","parameters","returnType")));clazz.put("x",c.get(3));clazz.put("y",c.get(4));expanded.add(clazz);
            }result.put("classes",expanded);
        }
        result.put("relations",expandMembers(value.get("relations"),List.of("sourceClassName","targetClassName","type","label","sourceMultiplicity","targetMultiplicity","associationClassName")));
        return result;
    }
    private static Object expandMembers(Object value,List<String> keys) {
        if(!(value instanceof List<?> list))return value;
        List<Object> result=new ArrayList<>();
        for(Object item:list){
            if(!(item instanceof List<?> fields)||fields.size()!=keys.size()){result.add(item);continue;}
            var expanded=new LinkedHashMap<String,Object>();for(int i=0;i<keys.size();i++)expanded.put(keys.get(i),fields.get(i));result.add(expanded);
        }return result;
    }

    private JsonNode post(String url,Map<String,String> headers,Map<String,Object> payload) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(Math.max(1,Math.min(30,timeoutSeconds))))
            .header("Content-Type","application/json").header("User-Agent","ArqNova/1.0");
        headers.forEach(builder::header);
        var response=http.send(builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()<200||response.statusCode()>=300){
            long cooldown=60_000;
            try{cooldown=Math.max(cooldown,Math.min(86_400,Long.parseLong(response.headers().firstValue("Retry-After").orElse("60")))*1000);}catch(NumberFormatException ignored){}
            String quota=response.body().contains("input tokens per minute")?"INPUT_TOKENS":response.body().contains("output tokens per minute")?"OUTPUT_TOKENS":"REQUESTS";
            if(response.statusCode()==429){
                var numbers=java.util.regex.Pattern.compile("(Limit|Used|Requested)\\s+([0-9,.]+)").matcher(response.body());
                List<String> amounts=new ArrayList<>();while(numbers.find())amounts.add(numbers.group(1)+"="+numbers.group(2));
                log.warn("UML quota limit: kind={} values={}",quota,amounts);
            }
            long retryAfterMillis=0;
            try{retryAfterMillis=Math.max(0,(long)Math.ceil(Double.parseDouble(response.headers().firstValue("Retry-After").orElse("0"))*1000));}catch(NumberFormatException ignored){}
            throw new UpstreamFailure(response.statusCode(),cooldown,quota,retryAfterMillis);
        }
        return mapper.readTree(response.body());
    }
    private String jsonObject(String text) throws Exception {
        String clean=text.trim();
        if(clean.startsWith("```")){int start=clean.indexOf('\n'),end=clean.lastIndexOf("```");if(start>=0&&end>start)clean=clean.substring(start+1,end).trim();}
        JsonNode node=mapper.readTree(clean);
        if(node==null||!node.isObject())throw new UpstreamFailure(502,60_000);
        return clean;
    }
    private static class UpstreamFailure extends Exception {
        final int status;final long cooldownMillis;final String quota;final long retryAfterMillis;
        UpstreamFailure(int status,long cooldown){this(status,cooldown,"UNKNOWN");}
        UpstreamFailure(int status,long cooldown,String quota){this(status,cooldown,quota,0);}
        UpstreamFailure(int status,long cooldown,String quota,long retryAfterMillis){super("AI provider status "+status+" limit="+quota);this.status=status;this.cooldownMillis=cooldown;this.quota=quota;this.retryAfterMillis=retryAfterMillis;}
    }
}
