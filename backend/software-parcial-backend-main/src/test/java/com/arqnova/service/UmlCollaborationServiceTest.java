package com.arqnova.service;

import com.arqnova.domain.model.UmlCollaborationSession;
import com.arqnova.dto.request.CreateUmlSessionRequest;
import com.arqnova.dto.request.UmlCollaborationEvent;
import com.arqnova.persistence.CollaborationSessionStore;
import com.arqnova.persistence.jpa.entity.UmlDiagramEntity;
import com.arqnova.persistence.jpa.entity.ProjectEntity;
import com.arqnova.persistence.jpa.entity.UmlCollaborationActivityEntity;
import com.arqnova.persistence.jpa.repository.UmlDiagramJpaRepository;
import com.arqnova.persistence.jpa.repository.UmlCollaborationActivityJpaRepository;
import com.arqnova.persistence.jpa.service.UmlProjectPersistenceService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

class UmlCollaborationServiceTest {
    @Test
    void persistsTraceableActivityButNeverPersistsCursorNoise() {
        CollaborationSessionStore store = mock(CollaborationSessionStore.class);
        UmlProjectPersistenceService projects = mock(UmlProjectPersistenceService.class);
        UmlDiagramJpaRepository diagrams = mock(UmlDiagramJpaRepository.class);
        UmlCollaborationActivityJpaRepository activities = mock(UmlCollaborationActivityJpaRepository.class);
        when(activities.existsById("event-1")).thenReturn(false);
        UmlCollaborationService service = new UmlCollaborationService(store, projects, diagrams, activities);
        UmlCollaborationEvent mutation = UmlCollaborationEvent.builder().eventId("event-1").sessionId("s1")
                .projectId("p1").userId("ana").userName("Ana Gómez").type("class:create")
                .elementId("c1").elementType("class").operation("creó una clase").timestamp(1000L)
                .data(Map.of("element", Map.of("id", "c1", "name", "Cliente"))).build();

        service.recordActivity(mutation);
        service.recordActivity(UmlCollaborationEvent.builder().eventId("cursor-1").sessionId("s1")
                .projectId("p1").userId("ana").userName("Ana Gómez").type("participant:cursor")
                .operation("moviendo el cursor").timestamp(1001L).build());

        verify(activities).save(any(UmlCollaborationActivityEntity.class));
        verify(activities, never()).existsById("cursor-1");
    }

    @Test
    void returnsPersistedActivityOnlyAfterProjectAccessCheck() {
        CollaborationSessionStore store = mock(CollaborationSessionStore.class);
        UmlProjectPersistenceService projects = mock(UmlProjectPersistenceService.class);
        UmlCollaborationActivityJpaRepository activities = mock(UmlCollaborationActivityJpaRepository.class);
        when(store.findById("s1")).thenReturn(Optional.of(UmlCollaborationSession.builder()
                .id("s1").projectId("p1").active(true).build()));
        when(activities.findTop100BySessionIdOrderByOccurredAtDesc("s1")).thenReturn(List.of(
                UmlCollaborationActivityEntity.builder().eventId("event-1").sessionId("s1").projectId("p1")
                        .userId("ana").userName("Ana Gómez").eventType("relation:delete")
                        .elementId("r1").elementType("relation").operation("eliminó una relación")
                        .occurredAt(java.time.LocalDateTime.of(2026, 9, 21, 10, 0)).build()));
        UmlCollaborationService service = new UmlCollaborationService(store, projects,
                mock(UmlDiagramJpaRepository.class), activities);

        var result = service.activities("s1", "ana");

        assertEquals("Ana Gómez", result.get(0).getUserName());
        assertEquals("r1", result.get(0).getElementId());
        verify(projects).requireAccess("p1", "ana");
    }

    @Test
    void sessionCreatorIsReturnedAsHostAndKeepsOwnerRole() {
        CollaborationSessionStore repository = mock(CollaborationSessionStore.class);
        UmlProjectPersistenceService projects = mock(UmlProjectPersistenceService.class);
        UmlDiagramJpaRepository diagrams = mock(UmlDiagramJpaRepository.class);
        when(repository.findActiveByProjectId("p1")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(projects.findProjectById("p1")).thenReturn(Optional.of(ProjectEntity.builder().id("p1").name("Proyecto").build()));
        when(diagrams.findByProjectId("p1")).thenReturn(Optional.of(UmlDiagramEntity.builder()
                .id("d1").projectId("p1").model(Map.of("classes", List.of(), "relations", List.of())).build()));
        CreateUmlSessionRequest request = new CreateUmlSessionRequest();
        request.setProjectId("p1");
        request.setProjectName("Proyecto");
        request.setModel(Map.of("classes", List.of(), "relations", List.of()));

        var response = new UmlCollaborationService(repository, projects, diagrams,
                mock(UmlCollaborationActivityJpaRepository.class)).start(request, "admin");

        assertEquals("admin", response.getHostUserId());
        assertEquals("OWNER", response.getMemberRole());
        verify(projects).requireAccess("p1", "admin", com.arqnova.persistence.jpa.entity.ProjectMemberRole.OWNER);
    }

    @Test
    @SuppressWarnings("unchecked")
    void appliesGranularClassMoveAndPersistsIt() {
        CollaborationSessionStore repository = mock(CollaborationSessionStore.class);
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("classes", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("id", "book", "name", "Book", "x", 10, "y", 20)))));
        original.put("relations", new ArrayList<>());
        UmlCollaborationSession session = UmlCollaborationSession.builder().id("s1").projectId("p1").active(true).model(original).build();
        when(repository.findById("s1")).thenReturn(Optional.of(session));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        UmlCollaborationService service = service(repository, session);
        Map<String, Object> moved = new LinkedHashMap<>(Map.of("id", "book", "name", "Book", "x", 230, "y", 140));

        service.apply(UmlCollaborationEvent.builder().type("class:move").sessionId("s1").projectId("p1")
                .elementId("book").data(Map.of("element", moved)).build());

        List<Map<String, Object>> classes = (List<Map<String, Object>>) session.getModel().get("classes");
        assertEquals(230, classes.get(0).get("x"));
        assertTrue(session.getUpdatedAt() != null);
        verify(repository).save(session);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsUpdatesAndDeletesRelationsWithoutReplacingTheDiagram() {
        CollaborationSessionStore repository = mock(CollaborationSessionStore.class);
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("classes", new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("id", "user", "name", "Usuario")),
                new LinkedHashMap<>(Map.of("id", "book", "name", "Libro")))));
        model.put("relations", new ArrayList<>());
        UmlCollaborationSession session = UmlCollaborationSession.builder().id("s1").projectId("p1").active(true).model(model).build();
        when(repository.findById("s1")).thenReturn(Optional.of(session));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        UmlCollaborationService service = service(repository, session);

        Map<String, Object> relation = new LinkedHashMap<>(Map.of(
                "id", "r1", "sourceId", "user", "targetId", "book", "type", "association"));
        service.apply(event("relation:create", "r1", relation));
        List<Map<String, Object>> relations = (List<Map<String, Object>>) session.getModel().get("relations");
        assertEquals(1, relations.size());

        relation.put("label", "consulta");
        service.apply(event("relation:update", "r1", relation));
        relations = (List<Map<String, Object>>) session.getModel().get("relations");
        assertEquals("consulta", relations.get(0).get("label"));

        service.apply(event("relation:delete", "r1", null));
        relations = (List<Map<String, Object>>) session.getModel().get("relations");
        assertTrue(relations.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deletingAClassAlsoDeletesItsRelations() {
        CollaborationSessionStore repository = mock(CollaborationSessionStore.class);
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("classes", new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("id", "user", "name", "Usuario")),
                new LinkedHashMap<>(Map.of("id", "book", "name", "Libro")))));
        model.put("relations", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of(
                "id", "r1", "sourceId", "user", "targetId", "book")))));
        UmlCollaborationSession session = UmlCollaborationSession.builder().id("s1").projectId("p1").active(true).model(model).build();
        when(repository.findById("s1")).thenReturn(Optional.of(session));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        UmlCollaborationService service = service(repository, session);

        service.apply(event("class:delete", "book", null));

        List<Map<String, Object>> classes = (List<Map<String, Object>>) session.getModel().get("classes");
        List<Map<String, Object>> relations = (List<Map<String, Object>>) session.getModel().get("relations");
        assertEquals(List.of("user"), classes.stream().map(item -> item.get("id")).toList());
        assertTrue(relations.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updatesDifferentAttributesWithoutLostUpdateInBothOrders() {
        for (boolean reverse : List.of(false, true)) {
            CollaborationSessionStore repository = mock(CollaborationSessionStore.class);
            UmlCollaborationSession session = attributeSession();
            when(repository.findById("s1")).thenReturn(Optional.of(session));
            when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
            UmlCollaborationService service = service(repository, session);
            UmlCollaborationEvent idUpdate = attributeEvent("attribute:update", "user", "attr-id",
                    Map.of("id", "attr-id", "name", "id", "type", "UUID", "isPrimaryKey", true));
            UmlCollaborationEvent emailUpdate = attributeEvent("attribute:update", "user", "attr-email",
                    Map.of("id", "attr-email", "name", "correo", "type", "Email", "isPrimaryKey", false));

            if (reverse) { service.apply(emailUpdate); service.apply(idUpdate); }
            else { service.apply(idUpdate); service.apply(emailUpdate); }

            List<Map<String, Object>> classes = (List<Map<String, Object>>) session.getModel().get("classes");
            List<Map<String, Object>> attributes = (List<Map<String, Object>>) classes.get(0).get("attributes");
            assertEquals(List.of("UUID", "String", "Email"), attributes.stream().map(item -> item.get("type")).toList());
            assertEquals("Usuario", classes.get(0).get("name"));
            assertEquals(100, classes.get(0).get("x"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsAndDeletesOnlyTheTargetAttribute() {
        CollaborationSessionStore repository = mock(CollaborationSessionStore.class);
        UmlCollaborationSession session = attributeSession();
        when(repository.findById("s1")).thenReturn(Optional.of(session));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        UmlCollaborationService service = service(repository, session);

        service.apply(attributeEvent("attribute:create", "user", "attr-phone",
                Map.of("id", "attr-phone", "name", "telefono", "type", "String", "isPrimaryKey", false)));
        service.apply(attributeEvent("attribute:delete", "user", "attr-id", null));

        List<Map<String, Object>> classes = (List<Map<String, Object>>) session.getModel().get("classes");
        List<Map<String, Object>> attributes = (List<Map<String, Object>>) classes.get(0).get("attributes");
        assertEquals(List.of("attr-name", "attr-email", "attr-phone"), attributes.stream().map(item -> item.get("id")).toList());
        assertEquals("metodoExistente", ((List<Map<String, Object>>) classes.get(0).get("methods")).get(0).get("name"));
    }

    private UmlCollaborationSession attributeSession() {
        List<Map<String, Object>> attributes = new ArrayList<>();
        attributes.add(new LinkedHashMap<>(Map.of("id", "attr-id", "name", "id", "type", "Long", "isPrimaryKey", true)));
        attributes.add(new LinkedHashMap<>(Map.of("id", "attr-name", "name", "nombre", "type", "String", "isPrimaryKey", false)));
        attributes.add(new LinkedHashMap<>(Map.of("id", "attr-email", "name", "correo", "type", "String", "isPrimaryKey", false)));
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", "user"); user.put("name", "Usuario"); user.put("x", 100); user.put("y", 120);
        user.put("attributes", attributes);
        user.put("methods", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("id", "m1", "name", "metodoExistente")))));
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("classes", new ArrayList<>(List.of(user)));
        model.put("relations", new ArrayList<>());
        return UmlCollaborationSession.builder().id("s1").projectId("p1").active(true).model(model).build();
    }

    private UmlCollaborationService service(CollaborationSessionStore store, UmlCollaborationSession session) {
        UmlDiagramJpaRepository diagrams = mock(UmlDiagramJpaRepository.class);
        UmlDiagramEntity diagram = UmlDiagramEntity.builder().id("d1").projectId("p1").model(session.getModel()).modelVersion(1).build();
        when(diagrams.findByProjectId("p1")).thenReturn(Optional.of(diagram));
        when(diagrams.findByProjectIdForUpdate("p1")).thenReturn(Optional.of(diagram));
        when(diagrams.save(any())).thenAnswer(call -> call.getArgument(0));
        return new UmlCollaborationService(store, mock(UmlProjectPersistenceService.class), diagrams,
                mock(UmlCollaborationActivityJpaRepository.class));
    }

    private UmlCollaborationEvent attributeEvent(String type, String classId, String attributeId, Map<String, Object> attribute) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("classId", classId);
        data.put("attributeId", attributeId);
        if (attribute != null) data.put("attribute", attribute);
        return UmlCollaborationEvent.builder().type(type).sessionId("s1").projectId("p1")
                .elementType("attribute").elementId(attributeId).data(data).build();
    }

    private UmlCollaborationEvent event(String type, String elementId, Map<String, Object> element) {
        Map<String, Object> data = element == null ? Map.of() : Map.of("element", element);
        return UmlCollaborationEvent.builder().type(type).sessionId("s1").projectId("p1")
                .elementId(elementId).data(data).build();
    }
}
