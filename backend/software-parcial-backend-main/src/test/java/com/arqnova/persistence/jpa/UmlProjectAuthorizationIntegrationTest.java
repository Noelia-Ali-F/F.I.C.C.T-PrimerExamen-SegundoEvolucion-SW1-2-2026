package com.arqnova.persistence.jpa;

import com.arqnova.exception.DuplicateResourceException;
import com.arqnova.exception.UnauthorizedActionException;
import com.arqnova.persistence.jpa.entity.ProjectMemberRole;
import com.arqnova.persistence.jpa.service.UmlProjectPersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class UmlProjectAuthorizationIntegrationTest {

    private static final String PROJECT = "phase3-authorization";

    @Autowired UmlProjectPersistenceService service;

    @AfterEach void cleanup() { service.deleteProject(PROJECT); }

    @Test
    void enforcesMembershipRolesAndModelVersion() {
        service.createProject(PROJECT, "Veterinaria", "Proyecto", "owner-a");
        service.addMember(PROJECT, "editor-b", ProjectMemberRole.EDITOR);
        service.addMember(PROJECT, "viewer-c", ProjectMemberRole.VIEWER);

        assertEquals(ProjectMemberRole.OWNER, service.requireAccess(PROJECT, "owner-a"));
        assertEquals(ProjectMemberRole.EDITOR, service.requireAccess(PROJECT, "editor-b"));
        assertEquals(ProjectMemberRole.VIEWER, service.requireAccess(PROJECT, "viewer-c"));
        assertThrows(UnauthorizedActionException.class, () -> service.requireAccess(PROJECT, "intruder-d"));
        assertThrows(UnauthorizedActionException.class, () ->
                service.requireAccess(PROJECT, "viewer-c", ProjectMemberRole.OWNER, ProjectMemberRole.EDITOR));

        Map<String,Object> model = Map.of("format", "uml-class-diagram", "version", 2,
                "title", "Veterinaria", "classes", List.of(Map.of("id", "mascota", "name", "Mascota")),
                "relations", List.of());
        var first = service.saveDiagram("diagram-" + PROJECT, PROJECT, model, 0);
        assertEquals(1, first.getModelVersion());
        assertThrows(DuplicateResourceException.class, () ->
                service.saveDiagram("diagram-" + PROJECT, PROJECT, model, 0));
        var second = service.saveDiagram("diagram-" + PROJECT, PROJECT, model, 1);
        assertEquals(2, second.getModelVersion());

        assertTrue(service.listAccessible("owner-a").stream().anyMatch(project -> PROJECT.equals(project.getId())));
        assertTrue(service.listAccessible("editor-b").stream().anyMatch(project -> PROJECT.equals(project.getId())));
        assertTrue(service.listAccessible("viewer-c").stream().anyMatch(project -> PROJECT.equals(project.getId())));
        assertFalse(service.listAccessible("intruder-d").stream().anyMatch(project -> PROJECT.equals(project.getId())));
        assertTrue(service.listAccessible("admin").stream().anyMatch(project -> PROJECT.equals(project.getId())));
        assertThrows(UnauthorizedActionException.class, () -> service.deleteProjectAuthorized(PROJECT, "editor-b"));
        service.deleteProjectAuthorized(PROJECT, "admin");
        assertFalse(service.findProjectById(PROJECT).isPresent());
        assertFalse(service.findDiagramByProjectId(PROJECT).isPresent());
    }
}
