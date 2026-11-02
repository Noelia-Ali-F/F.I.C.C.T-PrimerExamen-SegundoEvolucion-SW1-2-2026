package com.arqnova.repositorios.ia.memoria;
import com.arqnova.modelo.ia.ProjectPush.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class FcmHttpPushAdapterTest {
    @TempDir Path temp;final ObjectMapper json=new ObjectMapper();final HttpClient http=mock(HttpClient.class);
    final List<HttpRequest> requests=new ArrayList<>();KeyPair pair;Path account;
    final Clock clock=Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"),ZoneOffset.UTC);
    @BeforeEach void setup()throws Exception{
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);pair=generator.generateKeyPair();account=temp.resolve("test-service-account.json");
        Files.writeString(account,json.writeValueAsString(Map.of("type","service_account","project_id","arqnova-test","client_email","test@arqnova-test.iam.gserviceaccount.com","private_key","-----BEGIN PRIVATE KEY-----\n"+Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())+"\n-----END PRIVATE KEY-----")));
        when(http.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenAnswer(invocation->{
            HttpRequest request=invocation.getArgument(0);requests.add(request);
            return response(200,request.uri().getHost().equals("oauth2.googleapis.com")?"{\"access_token\":\"test-session\",\"expires_in\":3600}":"{\"name\":\"projects/arqnova-test/messages/test\"}");
        });
    }
    FcmHttpPushAdapter adapter(){return new FcmHttpPushAdapter(json,http,clock,true,"arqnova-test",account.toString());}
    @SuppressWarnings("unchecked") HttpResponse<String> response(int status,String body){var result=(HttpResponse<String>)mock(HttpResponse.class);when(result.statusCode()).thenReturn(status);when(result.body()).thenReturn(body);return result;}
    String body(HttpRequest request)throws Exception{
        var result=new CompletableFuture<String>();var bytes=new java.io.ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>(){
            public void onSubscribe(Flow.Subscription s){s.request(Long.MAX_VALUE);}
            public void onNext(ByteBuffer b){byte[] chunk=new byte[b.remaining()];b.get(chunk);bytes.writeBytes(chunk);}
            public void onError(Throwable error){result.completeExceptionally(error);}
            public void onComplete(){result.complete(bytes.toString(StandardCharsets.UTF_8));}
        });return result.get(2,TimeUnit.SECONDS);
    }
    @Test void signsOAuthAssertionAndUsesFidWithProjectMetadataWithoutExtraAuthCalls()throws Exception{
        var adapter=adapter();var event=new Event("event-1","project-1","Cambio UML","Clase creada");
        adapter.send("fid-test-001",event);adapter.send("fid-test-001",event);
        assertThat(requests).hasSize(3);
        String assertion=URLDecoder.decode(body(requests.get(0)).split("assertion=")[1],StandardCharsets.UTF_8);String[] parts=assertion.split("\\.");
        var verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(pair.getPublic());verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
        var claims=json.readTree(Base64.getUrlDecoder().decode(parts[1]));assertThat(claims.path("scope").asText()).isEqualTo("https://www.googleapis.com/auth/firebase.messaging");
        assertThat(claims.path("exp").asLong()).isEqualTo(clock.instant().plusSeconds(3600).getEpochSecond());
        var message=json.readTree(body(requests.get(1))).path("message");assertThat(message.path("fid").asText()).isEqualTo("fid-test-001");assertThat(message.has("token")).isFalse();
        assertThat(message.path("data").path("projectId").asText()).isEqualTo("project-1");assertThat(message.path("notification").path("body").asText()).isEqualTo("Clase creada");
        assertThat(requests.get(1).headers().firstValue("Authorization")).contains("Bearer test-session");
    }
    @Test void credentialsForAnotherProjectNeverProduceAnExternalRequest()throws Exception{
        var data=json.readTree(Files.readString(account));((com.fasterxml.jackson.databind.node.ObjectNode)data).put("project_id","another-project");Files.writeString(account,json.writeValueAsString(data));
        assertThatThrownBy(()->adapter().send("fid-test-001",new Event("e","p","t","b"))).isInstanceOf(IllegalArgumentException.class);assertThat(requests).isEmpty();
    }
    @Test void missingRuntimeCredentialFileKeepsPushDisabled(){
        var adapter=new FcmHttpPushAdapter(json,http,clock,true,"arqnova-test",temp.resolve("missing.json").toString());assertThat(adapter.enabled()).isFalse();
        assertThatThrownBy(()->adapter.send("fid-test-001",new Event("e","p","t","b"))).isInstanceOf(IllegalStateException.class);assertThat(requests).isEmpty();
    }
}
