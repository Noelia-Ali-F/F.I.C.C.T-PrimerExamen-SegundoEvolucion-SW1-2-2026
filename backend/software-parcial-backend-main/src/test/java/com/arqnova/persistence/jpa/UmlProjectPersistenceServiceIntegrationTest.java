package com.arqnova.persistence.jpa;

import com.arqnova.persistence.jpa.entity.ProjectMemberId;
import com.arqnova.persistence.jpa.entity.ProjectMemberRole;
import com.arqnova.persistence.jpa.entity.ProjectStatus;
import com.arqnova.persistence.jpa.entity.UmlDiagramEntity;
import com.arqnova.persistence.jpa.repository.ProjectJpaRepository;
import com.arqnova.persistence.jpa.repository.ProjectMemberJpaRepository;
import com.arqnova.persistence.jpa.repository.UmlDiagramJpaRepository;
import com.arqnova.persistence.jpa.service.UmlProjectPersistenceService;
import com.arqnova.exception.DuplicateResourceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class UmlProjectPersistenceServiceIntegrationTest {

    private static final String PROJECT_ID = "phase2-project-integration";
    private static final String DIAGRAM_ID = "phase2-diagram-integration";
    private static final String OWNER_ID = "mongo-user-admin";
    private static final String SECOND_PROJECT_ID = "phase2-project-integration-second";

    @Autowired UmlProjectPersistenceService service;
    @Autowired ProjectJpaRepository projectRepository;
    @Autowired ProjectMemberJpaRepository memberRepository;
    @Autowired UmlDiagramJpaRepository diagramRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        service.deleteProject(PROJECT_ID);
        service.deleteProject(SECOND_PROJECT_ID);
    }

    @Test
    void rejectsDuplicateProjectNamesForTheSameOwnerOnCreateAndUpdate() {
        service.createProject(PROJECT_ID, "  Biblioteca UML  ", "Primero", OWNER_ID);

        DuplicateResourceException createError = assertThrows(DuplicateResourceException.class,
                () -> service.createProject(SECOND_PROJECT_ID, "biblioteca uml", "Duplicado", OWNER_ID));
        assertEquals("Ya existe un proyecto UML con ese nombre", createError.getMessage());

        service.createProject(SECOND_PROJECT_ID, "Ventas UML", "Segundo", OWNER_ID);
        DuplicateResourceException updateError = assertThrows(DuplicateResourceException.class,
                () -> service.updateProject(SECOND_PROJECT_ID, OWNER_ID, "BIBLIOTECA UML", null, null));
        assertEquals("Ya existe un proyecto UML con ese nombre", updateError.getMessage());
        assertEquals("Ventas UML", service.findProjectById(SECOND_PROJECT_ID).orElseThrow().getName());
    }

    @Test
    void preservesUnknownImageAttributeTypeInPostgres() {
        service.createProject(PROJECT_ID,"CU7 sin tipo","Prueba",OWNER_ID);
        var model=map("format","uml-class-diagram","version",2,"classes",List.of(map("id","paciente","name","Paciente","attributes",List.of(map("id","attr-id","name","Id","type","","isPrimaryKey",false)))),"relations",List.of());
        service.saveDiagram(DIAGRAM_ID,PROJECT_ID,model,0);
        var loaded=service.findDiagramByProjectId(PROJECT_ID).orElseThrow();
        assertEquals(model,loaded.getModel());
        assertEquals("",jdbcTemplate.queryForObject("select model->'classes'->0->'attributes'->0->>'type' from uml_diagrams where project_id=?",String.class,PROJECT_ID));
        assertEquals(1,loaded.getModelVersion());
    }

    @Test
    void persistsCompleteUmlJsonAndDeletesDependantsByCascade() throws Exception {
        service.deleteProject(PROJECT_ID);

        var created = service.createProject(PROJECT_ID, "Pedidos", "Prueba PostgreSQL", OWNER_ID);
        assertEquals(PROJECT_ID, created.getId());
        assertEquals(ProjectStatus.ACTIVE, created.getStatus());
        assertNotNull(created.getCreatedAt());
        assertEquals(ProjectMemberRole.OWNER,
                memberRepository.findById(new ProjectMemberId(PROJECT_ID, OWNER_ID)).orElseThrow().getMemberRole());

        Map<String, Object> emptyModel = new LinkedHashMap<>();
        emptyModel.put("format", "arqnova-uml");
        emptyModel.put("version", 2);
        emptyModel.put("title", "Pedidos");
        emptyModel.put("classes", List.of());
        emptyModel.put("relations", List.of());
        service.saveDiagram(DIAGRAM_ID, PROJECT_ID, emptyModel);

        Map<String, Object> completeModel = completeModel();
        UmlDiagramEntity saved = service.saveDiagram(DIAGRAM_ID, PROJECT_ID, completeModel);
        LocalDateTime firstUpdatedAt = saved.getUpdatedAt();
        assertEquals(2, saved.getModelVersion());

        Map<String, Object> loaded = service.findDiagramByProjectId(PROJECT_ID).orElseThrow().getModel();
        assertEquals(completeModel, loaded);
        Map<?, ?> cliente = (Map<?, ?>) ((List<?>) loaded.get("classes")).get(0);
        Map<?, ?> attribute = (Map<?, ?>) ((List<?>) cliente.get("attributes")).get(0);
        Map<?, ?> method = (Map<?, ?>) ((List<?>) cliente.get("methods")).get(0);
        Map<?, ?> relation = (Map<?, ?>) ((List<?>) loaded.get("relations")).get(0);
        assertEquals("class-cliente", cliente.get("id"));
        assertEquals("attr-cliente-id", attribute.get("id"));
        assertEquals(Boolean.TRUE, attribute.get("isPrimaryKey"));
        assertEquals("method-comprar", method.get("id"));
        assertEquals("class-cliente", relation.get("sourceId"));
        assertEquals("class-pedido", relation.get("targetId"));
        assertEquals("1", relation.get("sourceMultiplicity"));
        assertEquals("0..*", relation.get("targetMultiplicity"));

        Map<String, Object> jsonbSummary = jdbcTemplate.queryForMap("""
                SELECT project_id,
                       jsonb_array_length(model -> 'classes') AS class_count,
                       jsonb_array_length(model -> 'relations') AS relation_count
                FROM uml_diagrams
                WHERE project_id = ?
                """, PROJECT_ID);
        assertEquals(PROJECT_ID, jsonbSummary.get("project_id"));
        assertEquals(2, ((Number) jsonbSummary.get("class_count")).intValue());
        assertEquals(1, ((Number) jsonbSummary.get("relation_count")).intValue());

        Thread.sleep(5L);
        Map<String, Object> modified = new LinkedHashMap<>(completeModel);
        modified.put("title", "Pedidos actualizado");
        UmlDiagramEntity updated = service.saveDiagram(DIAGRAM_ID, PROJECT_ID, modified);
        assertEquals(3, updated.getModelVersion());
        assertTrue(updated.getUpdatedAt().isAfter(firstUpdatedAt));

        service.addMember(PROJECT_ID, "mongo-user-editor", ProjectMemberRole.EDITOR);
        assertEquals(2, memberRepository.countByIdProjectId(PROJECT_ID));

        service.deleteProject(PROJECT_ID);
        assertFalse(projectRepository.existsById(PROJECT_ID));
        assertEquals(0, memberRepository.countByIdProjectId(PROJECT_ID));
        assertEquals(0, diagramRepository.countByProjectId(PROJECT_ID));
    }

    private Map<String, Object> completeModel() {
        Map<String, Object> idAttribute = map(
                "id", "attr-cliente-id", "name", "id", "type", "Long", "isPrimaryKey", true);
        Map<String, Object> method = map(
                "id", "method-comprar", "name", "comprar", "parameters", "pedidoId: Long", "returnType", "Pedido");
        Map<String, Object> cliente = map(
                "id", "class-cliente", "name", "Cliente", "description", "Comprador", "stereotype", "entity",
                "attributes", List.of(idAttribute), "methods", List.of(method), "responsibilities", List.of("Comprar"),
                "x", 120, "y", 80, "color", "#1e3a8a");
        Map<String, Object> pedido = map(
                "id", "class-pedido", "name", "Pedido", "description", "Orden", "stereotype", "entity",
                "attributes", List.of(map("id", "attr-pedido-id", "name", "id", "type", "Long", "isPrimaryKey", true)),
                "methods", List.of(), "responsibilities", List.of(), "x", 520, "y", 220, "color", "#2563eb");
        Map<String, Object> relation = map(
                "id", "relation-cliente-pedido", "sourceId", "class-cliente", "targetId", "class-pedido",
                "type", "association", "label", "realiza", "sourceMultiplicity", "1", "targetMultiplicity", "0..*");
        return map("format", "arqnova-uml", "version", 2, "title", "Pedidos", "updatedAt", "2026-09-18T00:00:00Z",
                "classes", List.of(cliente, pedido), "relations", List.of(relation));
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }
}
