package com.arqnova.repositorios.ia.memoria;

import com.arqnova.modelo.ia.AiConversation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="DYNAMODB_TEST_ENDPOINT",matches=".+")
class DynamoAiMemoryAdapterTest {
    static DynamoDbClient client;static String table="arqnova-ai-test-"+UUID.randomUUID();static ObjectMapper json;
    DynamoAiMemoryAdapter adapter;
    @BeforeAll static void database(){
        URI endpoint=URI.create(System.getenv("DYNAMODB_TEST_ENDPOINT"));
        if(!Set.of("localhost","127.0.0.1","dynamodb-local").contains(endpoint.getHost()))throw new IllegalArgumentException("Local only");
        client=DynamoDbClient.builder().region(Region.US_EAST_2).endpointOverride(endpoint)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local","local")))
                .httpClientBuilder(UrlConnectionHttpClient.builder()).build();
        client.createTable(r->r.tableName(table).billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
                .keySchema(KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build()));
        json=new ObjectMapper().registerModule(new JavaTimeModule());
    }
    @AfterAll static void cleanup(){if(client!=null){client.deleteTable(r->r.tableName(table));client.close();}}
    @BeforeEach void setup(){adapter=new DynamoAiMemoryAdapter(client,json,table);}
    Turn pending(String p,String user,String request,int offset){var now=Instant.parse("2026-11-01T12:00:00Z").plusSeconds(offset);return new Turn(request,p,user,user,Mode.ASK,"Pregunta",State.PENDING,null,null,3,now,now);}
    @Test void persistsHistoryAcrossAdapterInstancesAndKeepsUserIndex(){
        String p=UUID.randomUUID().toString();var turn=pending(p,"Noelia","request-001",0);
        assertThat(adapter.begin(turn).created()).isTrue();assertThat(adapter.begin(turn).created()).isFalse();
        var completed=turn.finish(new Answer("Respuesta",List.of(),"FAKE","test"),turn.createdAt().plusSeconds(1));
        assertThat(adapter.finish(completed)).isTrue();
        var reopened=new DynamoAiMemoryAdapter(client,json,table);
        assertThat(reopened.find(p,turn.requestId())).contains(completed);
        assertThat(reopened.recentByUser(p,"Noelia",10)).containsExactly(completed);
        assertThat(reopened.history(p,null,10).turns()).containsExactly(completed);
    }
    @Test void paginationIsOrderedAndScopedToProject(){
        String p=UUID.randomUUID().toString();
        for(int i=0;i<5;i++)adapter.begin(pending(p,"a","request-00"+i,i));
        adapter.begin(pending(UUID.randomUUID().toString(),"a","request-999",0));
        var first=adapter.history(p,null,2);assertThat(first.turns()).extracting(Turn::requestId).containsExactly("request-004","request-003");
        var second=adapter.history(p,first.nextCursor(),2);assertThat(second.turns()).extracting(Turn::requestId).containsExactly("request-002","request-001");
        var third=adapter.history(p,second.nextCursor(),2);assertThat(third.turns()).extracting(Turn::requestId).containsExactly("request-000");assertThat(third.nextCursor()).isNull();
    }
    @Test void userHistoryIsNotLimitedToRecentGroupMessages(){
        String p=UUID.randomUUID().toString();var old=pending(p,"old-user","request-000",0);adapter.begin(old);
        for(int i=1;i<8;i++)adapter.begin(pending(p,"another","request-00"+i,i));
        assertThat(adapter.recentByUser(p,"old-user",2)).containsExactly(old);
    }
    @Test void lateCompletionCannotOverwriteAnAlreadyFinalizedTurn(){
        String p=UUID.randomUUID().toString();var turn=pending(p,"a","request-001",0);adapter.begin(turn);
        var completed=turn.finish(new Answer("Resultado",List.of(),"FAKE","test"),turn.createdAt().plusSeconds(1));adapter.finish(completed);
        assertThat(adapter.finish(turn.fail("TIMEOUT",turn.createdAt().plusSeconds(2)))).isFalse();
        assertThat(adapter.find(p,turn.requestId())).contains(completed);
    }
    @Test void simultaneousRetriesReserveOneRequest()throws Exception{
        String p=UUID.randomUUID().toString();var turn=pending(p,"a","request-001",0);var pool=Executors.newFixedThreadPool(2);
        try{var results=pool.invokeAll(List.of(()->adapter.begin(turn),()->adapter.begin(turn)));
            int created=0;for(var result:results)if(((Submission)result.get()).created())created++;
            assertThat(created).isEqualTo(1);assertThat(adapter.history(p,null,20).turns()).hasSize(1);
        }finally{pool.shutdownNow();}
    }
    @Test void rejectsInvalidCursor(){assertThatThrownBy(()->adapter.history("p","invalid%%%",20)).isInstanceOf(IllegalArgumentException.class);}
    @Test void concurrentParticipantsCanCreateDistinctMessages()throws Exception{
        String p=UUID.randomUUID().toString();var pool=Executors.newFixedThreadPool(4);
        try{List<Callable<Submission>> calls=new ArrayList<>();for(int i=0;i<8;i++){int n=i;calls.add(()->adapter.begin(pending(p,"user-"+n,"request-00"+n,n)));}
            for(var result:pool.invokeAll(calls))assertThat(result.get().created()).isTrue();
            assertThat(adapter.history(p,null,20).turns()).hasSize(8);
        }finally{pool.shutdownNow();}
    }    @Test void leasesFenceConcurrentWorkersAndRecoverStagedResultAfterRestart() throws Exception {
        var turn = pending(UUID.randomUUID().toString(),"ana","lease-request",0);
        adapter.begin(turn);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var futures=pool.invokeAll(List.<Callable<Optional<Job>>>of(
                    () -> adapter.claim(turn,"one",100,220),
                    () -> adapter.claim(turn,"two",100,220)));
            var wins=new ArrayList<Job>();
            for(var future:futures) future.get().ifPresent(wins::add);
            assertThat(wins).hasSize(1);
            var old=wins.get(0);
            assertThat(adapter.renew(old,110,230)).isTrue();
            assertThat(adapter.claim(turn,"next",229,350)).isEmpty();
            var next=adapter.claim(turn,"next",231,350).orElseThrow();
            var result=turn.finish(new Answer("Durable",List.of(),"FAKE","test"),turn.createdAt());
            assertThat(adapter.stage(old,result,232)).isFalse();
            assertThat(adapter.complete(old,result,232)).isFalse();
            assertThat(adapter.stage(next,result,232)).isTrue();
            var restarted=new DynamoAiMemoryAdapter(client,json,table);
            var resumed=restarted.claim(turn,"restart",351,470).orElseThrow();
            assertThat(resumed.result()).isEqualTo(result);
            var foreign=pending(UUID.randomUUID().toString(),"ana","other-result",0)
                    .finish(new Answer("Ajena",List.of(),"FAKE","test"),turn.createdAt());
            assertThatThrownBy(()->restarted.stage(resumed,foreign,352)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->restarted.complete(resumed,foreign,352)).isInstanceOf(IllegalArgumentException.class);
            assertThat(restarted.complete(resumed,result,352)).isTrue();
            assertThat(restarted.find(turn.projectId(),turn.requestId())).contains(result);
            assertThat(restarted.claim(turn,"duplicate",471,590)).isEmpty();
        } finally {pool.shutdownNow();}
    }

    @Test void durableWorkerRetriesFinalWritesAndRecoversWithoutCallingProviderAgain() throws Exception {
        var spy=org.mockito.Mockito.spy(adapter);
        var projects=org.mockito.Mockito.mock(com.arqnova.servicios.puertos.entrada.ProjectUseCase.class);
        var users=org.mockito.Mockito.mock(com.arqnova.servicios.puertos.entrada.UserQueryUseCase.class);
        var provider=org.mockito.Mockito.mock(com.arqnova.servicios.puertos.salida.AiConversationProviderPort.class);
        String project=UUID.randomUUID().toString();
        org.mockito.Mockito.when(users.findByUsername("ana")).thenReturn(Optional.of(
                com.arqnova.modelo.Usuario.builder().username("ana").habilitado(true).build()));
        org.mockito.Mockito.when(projects.findProjectById(project)).thenReturn(Optional.of(
                com.arqnova.modelo.entidades.ProjectEntity.builder().id(project).name("Test").build()));
        org.mockito.Mockito.when(projects.findDiagramByProjectId(project)).thenReturn(Optional.empty());
        org.mockito.Mockito.when(provider.answer(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any()))
                .thenReturn(new Answer("Respuesta recuperable",List.of(),"FAKE","test"));
        org.mockito.Mockito.doThrow(new com.arqnova.modelo.excepciones.AiMemoryUnavailableException())
                .doThrow(new com.arqnova.modelo.excepciones.AiMemoryUnavailableException())
                .doThrow(new com.arqnova.modelo.excepciones.AiMemoryUnavailableException())
                .doCallRealMethod().when(spy).complete(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyLong());
        var queue=new ArrayList<Runnable>();
        var service=new com.arqnova.servicios.impl.AiConversationService(spy,provider,event->{},queue::add,projects,users,
                java.time.Clock.systemUTC());
        service.send(project,"ana","write-request",Mode.ASK,"Explica el modelo");
        queue.remove(0).run();
        assertThat(adapter.find(project,"write-request").orElseThrow().state()).isEqualTo(State.PENDING);
        // A separate service instance represents the recovered process.
        var restarted=new com.arqnova.servicios.impl.AiConversationService(spy,provider,event->{},queue::add,projects,users,
                java.time.Clock.systemUTC());
        for(int page=0;page<10 && adapter.find(project,"write-request").orElseThrow().state()==State.PENDING;page++){
            restarted.recoverPending();
            while(!queue.isEmpty())queue.remove(0).run();
        }
        assertThat(adapter.find(project,"write-request").orElseThrow().state()).isEqualTo(State.COMPLETED);
        org.mockito.Mockito.verify(provider,org.mockito.Mockito.times(1)).answer(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        var failedStage = org.mockito.Mockito.doThrow(new com.arqnova.modelo.excepciones.AiMemoryUnavailableException());
        for(int failure=1;failure<9;failure++) failedStage=failedStage.doThrow(new com.arqnova.modelo.excepciones.AiMemoryUnavailableException());
        failedStage.doCallRealMethod().when(spy).stage(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyLong());
        for(int crash=0;crash<4;crash++){
            var process=new com.arqnova.servicios.impl.AiConversationService(spy,provider,event->{},queue::add,projects,users,
                    java.time.Clock.systemUTC());
            process.send(project,"ana","lost-request",Mode.ASK,"Explica el modelo");
            while(!queue.isEmpty())queue.remove(0).run();
        }
        var lost=adapter.find(project,"lost-request").orElseThrow();
        assertThat(lost.state()).isEqualTo(State.FAILED);
        assertThat(lost.errorCode()).isEqualTo("AI_RECOVERY_EXHAUSTED");
        org.mockito.Mockito.verify(provider,org.mockito.Mockito.times(4)).answer(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doThrow(new com.arqnova.modelo.excepciones.AiMemoryUnavailableException())
                .doThrow(new com.arqnova.modelo.excepciones.AiMemoryUnavailableException())
                .doThrow(new com.arqnova.modelo.excepciones.AiMemoryUnavailableException())
                .doCallRealMethod().when(spy).stage(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyLong());
        service.send(project,"ana","buffered-request",Mode.ASK,"Explica el modelo");
        queue.remove(0).run();
        assertThat(adapter.find(project,"buffered-request").orElseThrow().state()).isEqualTo(State.PENDING);
        service.send(project,"ana","buffered-request",Mode.ASK,"Explica el modelo");
        queue.remove(0).run();
        assertThat(adapter.find(project,"buffered-request").orElseThrow().state()).isEqualTo(State.COMPLETED);
        org.mockito.Mockito.verify(provider,org.mockito.Mockito.times(5)).answer(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());

        org.mockito.Mockito.when(provider.answer(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any()))
                .thenThrow(new java.net.http.HttpTimeoutException("Interrupción simulada"))
                .thenReturn(new Answer("Reprocesada",List.of(),"FAKE","test"));
        service.send(project,"ana","provider-request",Mode.ASK,"Explica el modelo");
        queue.remove(0).run();
        assertThat(adapter.find(project,"provider-request").orElseThrow().state()).isEqualTo(State.PENDING);
        service.send(project,"ana","provider-request",Mode.ASK,"Explica el modelo");
        queue.remove(0).run();
        assertThat(adapter.find(project,"provider-request").orElseThrow().state()).isEqualTo(State.COMPLETED);


    }


}
