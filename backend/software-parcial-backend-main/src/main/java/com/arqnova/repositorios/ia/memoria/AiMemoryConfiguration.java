package com.arqnova.repositorios.ia.memoria;

import com.arqnova.modelo.ia.AiConversation.*;
import com.arqnova.modelo.excepciones.AiMemoryUnavailableException;
import com.arqnova.servicios.puertos.salida.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Configuration
public class AiMemoryConfiguration {
    @Bean @Profile("dynamodb") AiModelMutationPort unavailableDynamoModeling(){return new AiModelMutationPort(){
        public boolean enabled(){return false;}
        public Optional<Applied> find(Turn turn){return Optional.empty();}
        public Applied apply(Turn turn,Context context,Answer answer){throw new IllegalArgumentException("Modelado compartido no disponible en este perfil");}
    };}
    @Bean(destroyMethod="close") @ConditionalOnProperty(name="app.ai.conversations.enabled",havingValue="true")
    DynamoAiMemoryAdapter aiMemory(ObjectMapper json,@Value("${app.ai.conversations.table:arqnova-ai-memory}") String table,
            @Value("${app.ai.conversations.region:us-east-2}") String region,@Value("${app.ai.conversations.endpoint:}") String endpoint){
        var builder=DynamoDbClient.builder().region(Region.of(region)).overrideConfiguration(ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(15)).apiCallAttemptTimeout(Duration.ofSeconds(8)).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(4)).socketTimeout(Duration.ofSeconds(8)));
        if(endpoint.isBlank())builder.credentialsProvider(DefaultCredentialsProvider.create());
        else{
            URI uri=URI.create(endpoint);
            if(!Set.of("localhost","127.0.0.1","dynamodb-local").contains(uri.getHost()))throw new IllegalArgumentException("Endpoint local inválido");
            builder.endpointOverride(uri).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local","local")));
        }
        return new DynamoAiMemoryAdapter(builder.build(),json,table);
    }
    @Bean @ConditionalOnProperty(name="app.ai.conversations.enabled",havingValue="false",matchIfMissing=true)
    AiMemoryPort disabledAiMemory(){return new AiMemoryPort(){
        public boolean enabled(){return false;}
        public Submission begin(Turn v){throw new AiMemoryUnavailableException();}
        public Optional<Turn> find(String p,String r){throw new AiMemoryUnavailableException();}
        public Page history(String p,String c,int l){throw new AiMemoryUnavailableException();}
        public List<Turn> recentByUser(String p,String u,int l){throw new AiMemoryUnavailableException();}
        public boolean finish(Turn v){throw new AiMemoryUnavailableException();}
    };}
    @Bean(destroyMethod="shutdownNow") ThreadPoolExecutor aiConversationExecutor(){
        return new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),task->{
            Thread worker=new Thread(task,"arqnova-ai-conversation");worker.setDaemon(true);return worker;
        },new ThreadPoolExecutor.AbortPolicy());
    }
    @Bean AiTaskPort aiTaskPort(ThreadPoolExecutor aiConversationExecutor){return aiConversationExecutor::execute;}
}
