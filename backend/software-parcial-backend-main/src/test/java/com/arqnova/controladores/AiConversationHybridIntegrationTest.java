package com.arqnova.controladores;

import com.arqnova.modelo.*;
import com.arqnova.modelo.enums.RolUsuario;
import com.arqnova.modelo.ia.AiConversation.*;
import com.arqnova.modelo.dto.response.UmlAiOperationResponse;
import com.arqnova.modelo.entidades.ProjectMemberRole;
import com.arqnova.modelo.excepciones.DuplicateResourceException;
import com.arqnova.modelo.UmlCollaborationSession;
import com.arqnova.servicios.persistencia.UmlProjectPersistenceService;
import com.arqnova.servicios.puertos.salida.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.net.URI;
import java.util.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** PostgreSQL remains the project database while a separate real DynamoDB Local table stores AI memory. */
@SpringBootTest @AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="DYNAMODB_TEST_ENDPOINT",matches=".+")
class AiConversationHybridIntegrationTest {
    static final String TABLE="arqnova-ai-hybrid-"+UUID.randomUUID();static DynamoDbClient admin;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry props){
        URI uri=URI.create(System.getenv("DYNAMODB_TEST_ENDPOINT"));
        if(!Set.of("localhost","127.0.0.1").contains(uri.getHost()))throw new IllegalArgumentException("Local only");
        admin=DynamoDbClient.builder().region(Region.US_EAST_2).endpointOverride(uri)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local","local")))
                .httpClientBuilder(UrlConnectionHttpClient.builder()).build();
        admin.createTable(r->r.tableName(TABLE).billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
                .keySchema(KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build()));
        props.add("app.ai.conversations.enabled",()->true);props.add("app.ai.conversations.table",()->TABLE);props.add("app.ai.conversations.endpoint",uri::toString);
        props.add("app.ai.conversations.modeling-enabled",()->true);
    }
    @AfterAll static void cleanup(){if(admin!=null){admin.deleteTable(r->r.tableName(TABLE));admin.close();}}
    @Autowired MockMvc mvc;@Autowired UserStore users;@Autowired AuthTokenPort tokens;@Autowired UmlProjectPersistenceService projects;
    @Autowired AiMemoryPort memory;@Autowired PlatformTransactionManager transactions;
    @Autowired AiModelMutationPort mutations;
    @Autowired CollaborationSessionStore sessions;
    @Autowired UmlCollaborationActivityDataPort activityData;
    @Autowired com.arqnova.servicios.puertos.entrada.ElementLockUseCase locks;
    @MockBean AiConversationProviderPort provider;
    String project,username,token,outsider;
    @BeforeEach void setup()throws Exception{
        project="ai-hybrid-"+UUID.randomUUID();username="ai-owner-"+UUID.randomUUID();
        var user=users.save(Usuario.builder().username(username).password("test-placeholder").rol(RolUsuario.SOLICITANTE).habilitado(true).build());token=tokens.emitir(user);
        var other=users.save(Usuario.builder().username("ai-other-"+UUID.randomUUID()).password("test-placeholder").rol(RolUsuario.SOLICITANTE).habilitado(true).build());outsider=tokens.emitir(other);
        projects.createProject(project,project,"",username);projects.saveDiagram("diagram-"+project,project,Map.of("classes",List.of(),"relations",List.of()),0);
        when(provider.answer(any(),any())).thenReturn(new Answer("Prueba sin consumir APIs",List.of(),"FAKE","test"));
    }
    @AfterEach void cleanProject(){projects.deleteProject(project);users.findByUsername(username).ifPresent(users::delete);}
    String path(){return "/api/v1/projects/"+project+"/ai";}
    void awaitResult(String request)throws Exception{
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while(System.nanoTime()<deadline){
            var turn=memory.find(project,request);
            if(turn.isPresent()&&turn.get().state()!=State.PENDING){assertThat(turn.get().state()).isEqualTo(State.COMPLETED);return;}
            Thread.sleep(100);
        }
        fail("Conversation job did not finish");
    }
    @Test void savesOnlyAiMemoryToNoSqlAndPreservesJpaAndCanonicalDiagram()throws Exception{
        assertThat(transactions).isInstanceOf(org.springframework.orm.jpa.JpaTransactionManager.class);
        mvc.perform(post(path()+"/messages").header("Authorization","Bearer "+token).contentType("application/json")
                .content("{\"requestId\":\"request-001\",\"mode\":\"REVIEW\",\"instruction\":\"Revisa el diagrama\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.datos.authorId").value(username));
        awaitResult("request-001");
        mvc.perform(get(path()+"/messages").header("Authorization","Bearer "+token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.turns[0].answer.text").value("Prueba sin consumir APIs"));
        assertThat(projects.findDiagramByProjectId(project).orElseThrow().getModelVersion()).isEqualTo(1);
        assertThat(projects.findProjectById(project)).isPresent();
    }
    @Test void retriesAndForeignUsersCannotTriggerAnotherPaidRequest()throws Exception{
        String payload="{\"requestId\":\"request-001\",\"mode\":\"ASK\",\"instruction\":\"Explica UML\"}";
        mvc.perform(post(path()+"/messages").header("Authorization","Bearer "+token).contentType("application/json").content(payload)).andExpect(status().isAccepted());awaitResult("request-001");
        mvc.perform(post(path()+"/messages").header("Authorization","Bearer "+token).contentType("application/json").content(payload)).andExpect(status().isAccepted());
        mvc.perform(get(path()+"/messages").header("Authorization","Bearer "+outsider)).andExpect(status().isForbidden());
        verify(provider,times(1)).answer(any(),any());
    }
    @Test void authenticationAndInputValidationApplyToNewEndpoints()throws Exception{
        mvc.perform(get(path()+"/messages")).andExpect(status().isUnauthorized());
        mvc.perform(post(path()+"/messages").header("Authorization","Bearer "+token).contentType("application/json")
                .content("{\"requestId\":\"x\",\"mode\":\"ASK\",\"instruction\":\"\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(provider);
    }
    Turn modelTurn(String id){var now=Instant.now();return new Turn(id,project,username,username,Mode.MODEL,"Crea Cliente",State.PENDING,null,null,1,now,now);}
    Context modelContext(int version){return new Context(project,project,username,projects.findDiagramByProjectId(project).orElseThrow().getModel(),version,List.of(),List.of());}
    Answer createAnswer(){return new Answer("Clase creada",List.of(),"FAKE","test",List.of(UmlAiOperationResponse.builder().type("CLASS_CREATE").parameters(Map.of("id","cliente","name","Cliente")).build()));}
    @Test void modelRequestCommitsOnceAndKeepsReceiptBesideDiagram()throws Exception{
        when(provider.answer(any(),any())).thenReturn(createAnswer());
        String body="{\"requestId\":\"model-001\",\"mode\":\"MODEL\",\"instruction\":\"Crea Cliente\"}";
        mvc.perform(post(path()+"/messages").header("Authorization","Bearer "+token).contentType("application/json").content(body)).andExpect(status().isAccepted());awaitResult("model-001");
        mvc.perform(post(path()+"/messages").header("Authorization","Bearer "+token).contentType("application/json").content(body)).andExpect(status().isAccepted());
        assertThat(projects.findDiagramByProjectId(project).orElseThrow().getModelVersion()).isEqualTo(2);
        assertThat(mutations.find(modelTurn("model-001"))).get().extracting(AiModelMutationPort.Applied::diagramVersion).isEqualTo(2);
        assertThat(memory.find(project,"model-001").orElseThrow().diagramVersion()).isEqualTo(2);
        verify(provider,times(1)).answer(any(),any());
    }
    @Test void receiptMakesDirectConcurrentReplaysIdempotent()throws Exception{
        var turn=modelTurn("model-002");var context=modelContext(1);var answer=createAnswer();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            var first=pool.submit(()->mutations.apply(turn,context,answer));var second=pool.submit(()->mutations.apply(turn,context,answer));
            assertThat(first.get().diagramVersion()).isEqualTo(2);assertThat(second.get().diagramVersion()).isEqualTo(2);
        }finally{pool.shutdownNow();}
        assertThat(projects.findDiagramByProjectId(project).orElseThrow().getModelVersion()).isEqualTo(2);
    }
    @Test void staleVersionAndInvalidBatchNeverWriteReceiptOrPartialModel(){
        var turn=modelTurn("model-003");
        assertThatThrownBy(()->mutations.apply(turn,modelContext(0),createAnswer())).isInstanceOf(DuplicateResourceException.class);
        var invalid=new Answer("No debe guardarse",List.of(),"FAKE","test",List.of(
                createAnswer().operations().get(0),UmlAiOperationResponse.builder().type("RELATION_CREATE").parameters(Map.of("sourceId","cliente","targetId","missing")).build()));
        assertThatThrownBy(()->mutations.apply(turn,modelContext(1),invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThat(mutations.find(turn)).isEmpty();assertThat(projects.findDiagramByProjectId(project).orElseThrow().getModelVersion()).isEqualTo(1);
        assertThat(projects.findDiagramByProjectId(project).orElseThrow().getModel().get("classes")).isEqualTo(List.of());
    }
    @Test void recoversCommittedMutationIntoPendingNoSqlTurnWithoutAnotherProviderCall()throws Exception{
        var turn=modelTurn("model-004");memory.begin(turn);mutations.apply(turn,modelContext(1),createAnswer());
        mvc.perform(get(path()+"/messages/model-004").header("Authorization","Bearer "+token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.state").value("COMPLETED")).andExpect(jsonPath("$.datos.diagramVersion").value(2));
        verifyNoInteractions(provider);
    }
    @Test void viewerCannotRequestModeling(){
        String otherName=tokens.verificar(outsider).username();
        projects.addMember(project,otherName,ProjectMemberRole.VIEWER);
        assertThatThrownBy(()->mutations.apply(new Turn("model-005",project,otherName,otherName,Mode.MODEL,"Crea Cliente",State.PENDING,null,null,1,Instant.now(),Instant.now()),modelContext(1),createAnswer()))
                .isInstanceOf(com.arqnova.modelo.excepciones.UnauthorizedActionException.class);
        verifyNoInteractions(provider);
    }
    @Test void classMutationRespectsAnExistingAttributeLock(){
        projects.saveDiagram("diagram-"+project,project,Map.of("classes",List.of(Map.of("id","venta","name","Venta","attributes",List.of(Map.of("id","fecha","name","fecha","type","Date")),"methods",List.of())),"relations",List.of()),1);
        String sessionId=UUID.randomUUID().toString();var now=java.time.LocalDateTime.now();
        sessions.save(UmlCollaborationSession.builder().id(sessionId).projectId(project).inviteCode(UUID.randomUUID().toString().substring(0,10)).createdBy(username).active(true).model(modelContext(2).diagram()).createdAt(now).updatedAt(now).build());
        locks.acquire(project,sessionId,"fecha","attribute",username,username,"manual-test-socket");
        var answer=new Answer("Cambio",List.of(),"FAKE","test",List.of(UmlAiOperationResponse.builder().type("CLASS_UPDATE").targetId("venta").parameters(Map.of("name","VentaEditada")).build()));
        try{
            assertThatThrownBy(()->mutations.apply(modelTurn("model-006"),modelContext(2),answer)).isInstanceOf(DuplicateResourceException.class);
            assertThat(projects.findDiagramByProjectId(project).orElseThrow().getModelVersion()).isEqualTo(2);assertThat(mutations.find(modelTurn("model-006"))).isEmpty();
        }finally{locks.releaseBySocket("manual-test-socket");}
    }
    @Test void projectActivityIsPaginatedAndOnlyVisibleToMembers()throws Exception{
        String sessionId=UUID.randomUUID().toString();var now=java.time.LocalDateTime.now();
        sessions.save(UmlCollaborationSession.builder().id(sessionId).projectId(project).inviteCode(UUID.randomUUID().toString().substring(0,10)).createdBy(username).active(true).model(modelContext(1).diagram()).createdAt(now).updatedAt(now).build());
        for(String id:List.of("a-","b-"))activityData.save(com.arqnova.modelo.entidades.UmlCollaborationActivityEntity.builder()
                .eventId(id+project).sessionId(sessionId).projectId(project).userId(username).userName("Noelia")
                .eventType("class:create").operation("Creó Libro").occurredAt(now).build());
        String url="/api/v1/projects/"+project+"/activities";
        String response=mvc.perform(get(url).param("limit","1").header("Authorization","Bearer "+token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.items[0].author").value("Noelia")).andExpect(jsonPath("$.datos.items[0].id").value("b-"+project)).andReturn().getResponse().getContentAsString();
        String cursor=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("datos").path("nextCursor").asText();
        mvc.perform(get(url).param("limit","1").param("cursor",cursor).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.datos.items[0].id").value("a-"+project));
        mvc.perform(get(url).header("Authorization","Bearer "+outsider)).andExpect(status().isForbidden());
    }
}
