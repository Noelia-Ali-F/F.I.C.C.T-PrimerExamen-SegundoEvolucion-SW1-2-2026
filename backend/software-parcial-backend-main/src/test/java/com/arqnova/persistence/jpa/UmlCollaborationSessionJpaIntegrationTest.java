package com.arqnova.persistence.jpa;

import com.arqnova.persistence.jpa.entity.*;
import com.arqnova.persistence.jpa.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class UmlCollaborationSessionJpaIntegrationTest {
    @Autowired ProjectJpaRepository projects;
    @Autowired UmlCollaborationSessionJpaRepository sessions;
    @Autowired EntityManager entityManager;

    @Test
    void persistsQueriesDeactivatesAndCascadesWithJsonSnapshot() {
        String projectId = "it-collaboration-project";
        projects.saveAndFlush(ProjectEntity.builder().id(projectId).name("Integración CU3")
                .ownerUserId("admin").status(ProjectStatus.ACTIVE).build());
        sessions.saveAndFlush(UmlCollaborationSessionEntity.builder().id("it-session").projectId(projectId)
                .inviteCode("IT-UNIQUE-CODE").createdBy("admin").inviteRole(ProjectMemberRole.EDITOR)
                .active(true).modelSnapshot(Map.of("classes", java.util.List.of(), "relations", java.util.List.of())).build());

        assertEquals("it-session", sessions.findByInviteCodeAndActiveTrue("IT-UNIQUE-CODE").orElseThrow().getId());
        assertEquals("IT-UNIQUE-CODE", sessions.findFirstByProjectIdAndActiveTrue(projectId).orElseThrow().getInviteCode());
        assertTrue(sessions.findById("it-session").orElseThrow().getModelSnapshot().containsKey("classes"));
        var entity = sessions.findById("it-session").orElseThrow();
        entity.setActive(false);
        sessions.saveAndFlush(entity);
        assertTrue(sessions.findByInviteCodeAndActiveTrue("IT-UNIQUE-CODE").isEmpty());
    }

    @Test
    void projectDeleteCascadesSession() {
        String projectId = "it-collaboration-cascade";
        projects.saveAndFlush(ProjectEntity.builder().id(projectId).name("Cascade")
                .ownerUserId("admin").status(ProjectStatus.ACTIVE).build());
        sessions.saveAndFlush(UmlCollaborationSessionEntity.builder().id("it-session-cascade").projectId(projectId)
                .inviteCode("IT-CASCADE-CODE").createdBy("admin").inviteRole(ProjectMemberRole.VIEWER).active(true).build());
        projects.deleteById(projectId);
        projects.flush();
        entityManager.clear();
        assertFalse(sessions.findById("it-session-cascade").isPresent());
    }
}
