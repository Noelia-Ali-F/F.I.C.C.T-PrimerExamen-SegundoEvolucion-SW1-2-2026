package com.arqnova.repositorios.ia.memoria;
import com.arqnova.modelo.ia.ProjectPush.Event;
import com.arqnova.servicios.puertos.salida.PushDeliveryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.*;
import java.util.*;

/** FCM HTTP v1 with short-lived OAuth credentials; the private key is read from a runtime file. */
@Component
public class FcmHttpPushAdapter implements PushDeliveryPort {
    private static final String TOKEN_URL="https://oauth2.googleapis.com/token";
    private final ObjectMapper json;private final HttpClient http;private final Clock clock;
    private final boolean configured;private final String project,path;
    private String cachedToken;private Instant tokenExpiry=Instant.EPOCH;
    @org.springframework.beans.factory.annotation.Autowired
    public FcmHttpPushAdapter(ObjectMapper json,@Value("${app.push.enabled:false}")boolean enabled,
            @Value("${app.push.firebase-project:}")String project,@Value("${app.push.credentials-file:}")String path){
        this(json,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),Clock.systemUTC(),enabled,project,path);
    }
    FcmHttpPushAdapter(ObjectMapper json,HttpClient http,Clock clock,boolean enabled,String project,String path){this.json=json;this.http=http;this.clock=clock;this.configured=enabled;this.project=project;this.path=path;}
    public boolean enabled(){return configured&&project.matches("[a-z][a-z0-9-]{4,62}")&&!path.isBlank()&&Files.isRegularFile(Path.of(path));}
    public void send(String fid,Event event)throws Exception{
        if(!enabled())throw new IllegalStateException("Firebase push no configurado");
        var message=Map.of("fid",fid,"notification",Map.of("title",clip(event.title(),100),"body",clip(event.body(),180)),
                "data",Map.of("projectId",event.projectId(),"eventId",event.id()));
        String body=json.writeValueAsString(Map.of("message",message));
        var request=HttpRequest.newBuilder(URI.create("https://fcm.googleapis.com/v1/projects/"+project+"/messages:send"))
                .timeout(Duration.ofSeconds(12)).header("Authorization","Bearer "+accessToken()).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()==401){synchronized(this){cachedToken=null;tokenExpiry=Instant.EPOCH;}}
        if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("Firebase push HTTP "+response.statusCode());
    }
    private synchronized String accessToken()throws Exception{
        Instant now=clock.instant();if(cachedToken!=null&&tokenExpiry.isAfter(now.plusSeconds(60)))return cachedToken;
        var account=json.readTree(Files.readString(Path.of(path)));
        if(!project.equals(account.path("project_id").asText())||!"service_account".equals(account.path("type").asText()))throw new IllegalArgumentException("Credenciales de otro proyecto Firebase");
        String email=account.path("client_email").asText(),key=account.path("private_key").asText();
        if(email.isBlank()||key.isBlank())throw new IllegalArgumentException("Credenciales Firebase incompletas");
        String header=encode(json.writeValueAsBytes(Map.of("alg","RS256","typ","JWT")));
        String payload=encode(json.writeValueAsBytes(Map.of("iss",email,"scope","https://www.googleapis.com/auth/firebase.messaging","aud",TOKEN_URL,"iat",now.getEpochSecond(),"exp",now.plusSeconds(3600).getEpochSecond())));
        var privateKey=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(key.replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----","").replaceAll("\\s",""))));
        var signature=Signature.getInstance("SHA256withRSA");signature.initSign(privateKey);signature.update((header+"."+payload).getBytes(StandardCharsets.UTF_8));
        String assertion=header+"."+payload+"."+encode(signature.sign());
        String form="grant_type="+URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer",StandardCharsets.UTF_8)+"&assertion="+URLEncoder.encode(assertion,StandardCharsets.UTF_8);
        var request=HttpRequest.newBuilder(URI.create(TOKEN_URL)).timeout(Duration.ofSeconds(12)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)throw new IllegalStateException("OAuth Firebase HTTP "+response.statusCode());
        var result=json.readTree(response.body());String token=result.path("access_token").asText();
        if(token.isBlank())throw new IllegalStateException("OAuth Firebase no devolvió una sesión");
        cachedToken=token;tokenExpiry=now.plusSeconds(Math.min(3600,Math.max(1,result.path("expires_in").asLong(3600))));return cachedToken;
    }
    private String encode(byte[] value){return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
    private String clip(String value,int max){return value==null?"":value.substring(0,Math.min(value.length(),max));}
}
