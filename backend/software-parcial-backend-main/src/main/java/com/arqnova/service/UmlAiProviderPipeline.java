package com.arqnova.service;

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
    private final ObjectMapper mapper;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    // All three Gemini keys belong to ONE project. A quota error cools down the whole project.
    private final AtomicLong geminiUnavailableUntil=new AtomicLong();
    private final AtomicLong groqUnavailableUntil=new AtomicLong();
    private final Map<Integer,Long> invalidKeysUntil=new ConcurrentHashMap<>();
    public record Result(String json,String provider,String model,boolean fallbackUsed) {}
    public UmlAiProviderPipeline(ObjectMapper mapper){this.mapper=mapper;}
    public boolean isConfigured(){return !geminiKeys().isEmpty()||!groqKey.isBlank();}
    private List<String> geminiKeys(){return java.util.stream.Stream.of(geminiKey1,geminiKey2,geminiKey3).map(String::trim).filter(k->!k.isEmpty()).distinct().toList();}

    public Result generate(String prompt,Map<String,Object> schema,String imageUrl) throws Exception {
        List<String> keys=geminiKeys();
        int attempts=0,lastStatus=503;
        if(System.currentTimeMillis()>=geminiUnavailableUntil.get()){
            for(int i=0;i<keys.size();i++){
                if(System.currentTimeMillis()<invalidKeysUntil.getOrDefault(i,0L))continue;
                attempts++;
                try{
                    String json=gemini(prompt,schema,imageUrl,keys.get(i));
                    return new Result(json,"GEMINI",geminiModel,attempts>1);
                }catch(InterruptedException e){Thread.currentThread().interrupt();throw e;
                }catch(UpstreamFailure e){
                    lastStatus=e.status;
                    log.warn("UML provider failed: provider=GEMINI credential={} status={}",i+1,e.status);
                    if(e.status==401||e.status==403){invalidKeysUntil.put(i,System.currentTimeMillis()+300_000);continue;}
                    // Another key of the same project cannot add quota or fix a shared outage.
                    geminiUnavailableUntil.set(System.currentTimeMillis()+e.cooldownMillis);
                    break;
                }catch(java.io.IOException e){
                    log.warn("UML provider failed: provider=GEMINI type={}",e.getClass().getSimpleName());
                    geminiUnavailableUntil.set(System.currentTimeMillis()+30_000);break;
                }
            }
        }else lastStatus=429;
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
        List<Map<String,Object>> parts=new ArrayList<>();parts.add(Map.of("text",prompt));
        if(imageUrl!=null){int marker=imageUrl.indexOf(";base64,");if(marker<0)throw new IllegalArgumentException("Imagen inválida");parts.add(Map.of("inlineData",Map.of("mimeType",imageUrl.substring(5,marker),"data",imageUrl.substring(marker+8))));}
        Map<String,Object> generation=new LinkedHashMap<>();
        if(!geminiModel.startsWith("gemini-3"))generation.put("temperature",0);generation.put("maxOutputTokens",8192);
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

    private String groq(String prompt,Map<String,Object> schema,String imageUrl,String selectedModel) throws Exception {
        List<Map<String,Object>> content=new ArrayList<>();
        content.add(Map.of("type","text","text",prompt+"\nDevuelve exclusivamente un objeto JSON con este JSON Schema: "+mapper.writeValueAsString(schema)));
        if(imageUrl!=null)content.add(Map.of("type","image_url","image_url",Map.of("url",imageUrl)));
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("model",selectedModel);payload.put("temperature",0);payload.put("max_completion_tokens",8192);
        payload.put("messages",List.of(Map.of("role","user","content",content)));
        if(imageUrl==null&&selectedModel.startsWith("openai/gpt-oss-")){
            payload.put("reasoning_effort","low");
            payload.put("response_format",Map.of("type","json_schema","json_schema",Map.of("name","uml_command","strict",true,"schema",schema)));
        }else payload.put("response_format",Map.of("type","json_object"));
        JsonNode data=post(groqBaseUrl+"/chat/completions",Map.of("Authorization","Bearer "+groqKey),payload);
        JsonNode choice=data.path("choices").path(0);
        if(!"stop".equals(choice.path("finish_reason").asText()))throw new UpstreamFailure(502,60_000);
        return jsonObject(choice.path("message").path("content").asText(""));
    }

    private JsonNode post(String url,Map<String,String> headers,Map<String,Object> payload) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(Math.max(1,Math.min(30,timeoutSeconds))))
            .header("Content-Type","application/json").header("User-Agent","ArqNova/1.0");
        headers.forEach(builder::header);
        var response=http.send(builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()<200||response.statusCode()>=300){
            long cooldown=60_000;
            try{cooldown=Math.max(cooldown,Math.min(86_400,Long.parseLong(response.headers().firstValue("Retry-After").orElse("60")))*1000);}catch(NumberFormatException ignored){}
            throw new UpstreamFailure(response.statusCode(),cooldown);
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
        final int status;final long cooldownMillis;
        UpstreamFailure(int status,long cooldown){super("AI provider status "+status);this.status=status;this.cooldownMillis=cooldown;}
    }
}
