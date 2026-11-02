package com.arqnova.controladores;

import com.arqnova.modelo.*;
import com.arqnova.modelo.enums.RolUsuario;
import com.arqnova.modelo.ia.AiConversation.*;
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
    }
    @AfterAll static void cleanup(){if(admin!=null){admin.deleteTable(r->r.tableName(TABLE));admin.close();}}
    @Autowired MockMvc mvc;@Autowired UserStore users;@Autowired AuthTokenPort tokens;@Autowired UmlProjectPersistenceService projects;
    @Autowired AiMemoryPort memory;@Autowired PlatformTransactionManager transactions;
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
        for(int i=0;i<80;i++){
            var turn=memory.find(project,request);
            if(turn.isPresent()&&turn.get().state()!=State.PENDING){assertThat(turn.get().state()).isEqualTo(State.COMPLETED);return;}
            Thread.sleep(50);
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
}
