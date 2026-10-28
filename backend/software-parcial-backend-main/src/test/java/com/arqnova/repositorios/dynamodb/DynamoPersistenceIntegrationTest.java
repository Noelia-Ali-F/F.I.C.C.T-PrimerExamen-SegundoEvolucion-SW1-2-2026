package com.arqnova.repositorios.dynamodb;

import com.arqnova.modelo.*;
import com.arqnova.modelo.entidades.*;
import com.arqnova.modelo.enums.RolUsuario;
import com.arqnova.modelo.excepciones.*;
import com.arqnova.modelo.dto.request.*;
import com.arqnova.modelo.dto.response.*;
import com.arqnova.servicios.AuthService;
import com.arqnova.servicios.UmlCollaborationService;
import com.arqnova.servicios.persistencia.UmlProjectPersistenceService;
import com.arqnova.servicios.puertos.salida.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Real SDK against DynamoDB Local, with an isolated random table; no production or AI calls. */
@SpringBootTest
@ActiveProfiles("dynamodb")
@EnabledIfEnvironmentVariable(named = "DYNAMODB_TEST_ENDPOINT", matches = ".+")
class DynamoPersistenceIntegrationTest {
    private static final String TABLE = "arqnova-test-" + UUID.randomUUID();
    private static DynamoDbClient admin;
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String endpoint = System.getenv("DYNAMODB_TEST_ENDPOINT");
        URI uri = URI.create(endpoint);
        if (!Set.of("localhost", "127.0.0.1", "dynamodb-local").contains(uri.getHost())) throw new IllegalArgumentException("Solo pruebas locales");
        admin = DynamoDbClient.builder().region(Region.US_EAST_2).endpointOverride(uri)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .httpClientBuilder(UrlConnectionHttpClient.builder()).build();
        admin.createTable(CreateTableRequest.builder().tableName(TABLE).billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(), AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
                .keySchema(KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(), KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build()).build());
        properties.add("app.dynamodb.table", () -> TABLE); properties.add("app.dynamodb.endpoint", () -> endpoint);
    }
    @AfterAll static void cleanup() { if (admin != null) { admin.deleteTable(r -> r.tableName(TABLE)); admin.close(); } }
    @Autowired UmlProjectPersistenceService projects;
    @Autowired ProjectDataPort projectData;
    @Autowired ProjectMemberDataPort members;
    @Autowired UmlDiagramDataPort diagrams;
    @Autowired UserDataPort users;
    @Autowired UserStore userStore;
    @Autowired DepartamentoStore departments;
    @Autowired CollaborationSessionStore sessions;
    @Autowired UmlCollaborationActivityDataPort activities;
    @Autowired UmlCollaborationService collaboration;
    @Autowired AuthService auth;
    @Autowired DynamoDocumentStore documents;
    @Autowired PlatformTransactionManager transactions;
    private String id() { return UUID.randomUUID().toString(); }
    private String project() { String p = id(); projects.createProject(p, p, "Prueba", "owner"); return p; }
    private Map<String,Object> model() { return Map.of("classes", List.of(Map.of("id", "a", "name", "Catálogo")), "relations", List.of()); }
    private UmlCollaborationSession session(String p) { return sessions.save(UmlCollaborationSession.builder().id(id()).projectId(p).inviteCode(id()).createdBy("owner").inviteRole("EDITOR").active(true).model(model()).build()); }

    @Test void preservesMembershipPermissionsAndDiagramVersions() {
        String p = project(); projects.addMember(p, "viewer", ProjectMemberRole.VIEWER);
        assertThat(projects.requireAccess(p, "owner")).isEqualTo(ProjectMemberRole.OWNER);
        assertThat(projects.requireAccess(p, "viewer")).isEqualTo(ProjectMemberRole.VIEWER);
        assertThatThrownBy(() -> projects.requireAccess(p, "intruder")).isInstanceOf(UnauthorizedActionException.class);
        assertThatThrownBy(() -> projects.requireAccess(p, "viewer", ProjectMemberRole.EDITOR)).isInstanceOf(UnauthorizedActionException.class);
        assertThat(projects.saveDiagram(id(), p, model(), 0).getModelVersion()).isEqualTo(1);
        assertThatThrownBy(() -> projects.saveDiagram(id(), p, model(), 0)).isInstanceOf(DuplicateResourceException.class);
        assertThat(projects.saveDiagram(id(), p, model(), 1).getModelVersion()).isEqualTo(2);
        assertThat(diagrams.findByProjectId(p).orElseThrow().getModel()).isEqualTo(model());
    }
    @Test void rollsBackEveryDocumentAndUniqueReservationOnFailure() {
        String p = id();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            projects.createProject(p, p, "", "owner");
            projects.saveDiagram(id(), p, model(), 0);
            throw new IllegalArgumentException("Simular fallo después de guardar");
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(projectData.existsById(p)).isFalse(); assertThat(members.countByIdProjectId(p)).isZero();
        assertThat(diagrams.existsByProjectId(p)).isFalse();
        assertThat(projects.createProject(p, p, "", "owner").getId()).isEqualTo(p);
    }
    @Test void twoConcurrentWritersCannotOverwriteEachOther() throws Exception {
        String p = project(); projects.saveDiagram(id(), p, model(), 0);
        var barrier = new CyclicBarrier(2); var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> writer = () -> {
                try { new TransactionTemplate(transactions).execute(status -> {
                    var d = diagrams.findByProjectIdForUpdate(p).orElseThrow();
                    try { barrier.await(10, TimeUnit.SECONDS); } catch (Exception e) { throw new IllegalStateException(e); }
                    d.setModelVersion(d.getModelVersion() + 1); diagrams.save(d); return null;
                }); return true; } catch (DuplicateResourceException e) { return false; }
            };
            var result = pool.invokeAll(List.of(writer, writer));
            assertThat(result.stream().filter(f -> { try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); } }).count()).isEqualTo(1);
            assertThat(diagrams.findByProjectId(p).orElseThrow().getModelVersion()).isEqualTo(2);
        } finally { pool.shutdownNow(); }
    }
    @Test void projectNamesAreUniquePerOwnerAndCaseInsensitive() {
        String name = id(); projects.createProject(id(), name, "", "owner-a");
        assertThatThrownBy(() -> projects.createProject(id(), name.toUpperCase(Locale.ROOT), "", "owner-a")).isInstanceOf(DuplicateResourceException.class);
        assertThat(projects.createProject(id(), name, "", "owner-b")).isNotNull();
    }
    @Test void deletingProjectCascadesAndReleasesNamesAndInvitations() {
        String p = project(); projects.saveDiagram(id(), p, model(), 0); var s = session(p);
        activities.save(UmlCollaborationActivityEntity.builder().eventId(id()).sessionId(s.getId()).projectId(p).userId("owner").userName("Owner").eventType("class:create").operation("Crear").occurredAt(LocalDateTime.now()).build());
        projects.deleteProjectAuthorized(p, "owner");
        assertThat(members.countByIdProjectId(p)).isZero(); assertThat(diagrams.existsByProjectId(p)).isFalse();
        assertThat(sessions.findById(s.getId())).isEmpty(); assertThat(sessions.findActiveByInviteCode(s.getInviteCode())).isEmpty();
        assertThat(activities.findAll().stream().filter(v -> v.getProjectId().equals(p))).isEmpty();
        projects.createProject(p, p, "", "owner"); sessions.save(s);
    }
    @Test void rejectsOrphanDocuments() {
        assertThatThrownBy(() -> members.save(ProjectMemberEntity.builder().id(new ProjectMemberId(id(), "x")).memberRole(ProjectMemberRole.EDITOR).build())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> session(id())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void authenticatesUsingExistingPasswordHashAndUserIdentity() {
        String username = id() + "@example.test";
        var hash = new com.arqnova.repositorios.seguridad.PasswordService().encode("Only-for-this-test");
        userStore.save(Usuario.builder().id(id()).username(username).password(hash).nombreCompleto("Prueba").rol(RolUsuario.SOLICITANTE).habilitado(true).build());
        var request = new LoginRequest(); request.setUsername(username); request.setPassword("Only-for-this-test");
        assertThat(auth.login(request)).isNotNull();
        assertThat(userStore.findByUsername(username.toUpperCase(Locale.ROOT)).orElseThrow().getPassword()).isEqualTo(hash);
        assertThatThrownBy(() -> users.save(UserEntity.builder().id(id()).username(username.toUpperCase(Locale.ROOT)).passwordHash(hash).role(RolUsuario.SOLICITANTE).enabled(true).build())).isInstanceOf(DuplicateResourceException.class);
    }
    @Test void preservesDepartmentsSoftDeleteAndRename() {
        String name = id(); var d = departments.save(Departamento.builder().nombre(name).activo(true).build());
        assertThat(departments.findByNombreIgnoreCase(name.toUpperCase(Locale.ROOT))).isPresent();
        d.setNombre(id()); d.setActivo(false); departments.save(d);
        assertThat(departments.findByNombreIgnoreCase(name)).isEmpty();
        assertThat(departments.findAllByActivoTrueOrderByNombreAsc()).noneMatch(v -> v.getId().equals(d.getId()));
        assertThat(departments.findById(d.getId()).orElseThrow().getFechaCreacion()).isEqualTo(d.getFechaCreacion());
    }
    @Test void persistsModelsLargerThanAnIndividualDynamoItemAndCleansOldChunks() {
        String p = project(); byte[] random = new byte[650_000]; new Random(123).nextBytes(random);
        Map<String,Object> large = Map.of("classes", List.of(), "notes", Base64.getEncoder().encodeToString(random));
        projects.saveDiagram(id(), p, large, 0);
        assertThat(diagrams.findByProjectId(p).orElseThrow().getModel()).isEqualTo(large);
        projects.saveDiagram(id(), p, model(), 1);
        assertThat(diagrams.findByProjectId(p).orElseThrow().getModel()).isEqualTo(model());
    }
    @Test void refusesOversizedTransactionWithoutPartialWrites() {
        String p = id();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            projects.createProject(p, p, "", "owner");
            for (int i = 0; i < 105; i++) projects.addMember(p, "member-" + i, ProjectMemberRole.EDITOR);
            return null;
        })).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("transacción DynamoDB");
        assertThat(projectData.existsById(p)).isFalse(); assertThat(members.countByIdProjectId(p)).isZero();
    }
    @Test void loadsEveryQueryPage() {
        String kind = "PAGINATION-" + id(); byte[] bytes = new byte[40_000]; new Random(7).nextBytes(bytes);
        String contents = Base64.getEncoder().encodeToString(bytes);
        for (int i = 0; i < 30; i++) documents.put(kind, String.valueOf(i), Map.of("data", contents, "id", i));
        var firstPage = admin.query(r -> r.tableName(TABLE).keyConditionExpression("pk = :kind")
                .expressionAttributeValues(Map.of(":kind", AttributeValue.builder().s(kind).build())));
        assertThat(firstPage.lastEvaluatedKey()).isNotEmpty();
        assertThat(documents.list(kind, Map.class)).hasSize(30);
    }
    @Test void caughtInnerFailureStillPreventsPartialCommit() {
        String p = id();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            projects.createProject(p, p, "", "owner");
            try { session(id()); } catch (IllegalArgumentException expected) { /* Application attempts to recover. */ }
            return null;
        })).isInstanceOf(org.springframework.transaction.UnexpectedRollbackException.class);
        assertThat(projectData.existsById(p)).isFalse();
    }
    @Test void storesCollaborationEventAndCanonicalModelAtomically() {
        String p = project(); projects.saveDiagram(id(), p, model(), 0); var s = session(p);
        var event = UmlCollaborationEvent.builder().eventId(id()).sessionId(s.getId()).projectId(p).userId("owner").userName("Owner")
                .type("class:create").operation("Crear").elementId("b").elementType("class")
                .data(Map.of("element", Map.of("id", "b", "name", "Reserva", "attributes", List.of()))).build();
        collaboration.apply(event);
        assertThat(diagrams.findByProjectId(p).orElseThrow().getModel().get("classes").toString()).contains("Reserva");
        assertThat(activities.existsById(event.getEventId())).isTrue();
        assertThat(collaboration.activities(s.getId(), "owner")).hasSize(1);
    }
    @Test void simultaneousWebSocketEditsKeepEveryClassAndActivity() throws Exception {
        String p = project(); projects.saveDiagram(id(), p, model(), 0); var s = session(p);
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> edits = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                String element = "concurrent-" + index;
                edits.add(() -> {
                    collaboration.apply(UmlCollaborationEvent.builder().eventId(id()).sessionId(s.getId()).projectId(p)
                            .userId("owner").userName("Owner").type("class:create").operation("Crear").elementId(element).elementType("class")
                            .data(Map.of("element", Map.of("id", element, "name", element, "attributes", List.of()))).build());
                    return null;
                });
            }
            for (var result : pool.invokeAll(edits)) result.get(30, TimeUnit.SECONDS);
            var saved = diagrams.findByProjectId(p).orElseThrow();
            assertThat((List<?>) saved.getModel().get("classes")).hasSize(13);
            assertThat(saved.getModelVersion()).isEqualTo(13);
            assertThat(collaboration.activities(s.getId(), "owner")).hasSize(12);
        } finally { pool.shutdownNow(); }
    }
}
