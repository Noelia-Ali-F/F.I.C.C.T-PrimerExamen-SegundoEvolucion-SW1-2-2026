package com.arqnova.repositorios.dynamodb;

import com.arqnova.modelo.entidades.*;
import com.arqnova.modelo.*;
import com.arqnova.servicios.puertos.salida.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Optional end-to-end rehearsal: data written by deploy/migrate_dynamodb.py is read through Java ports. */
@SpringBootTest @ActiveProfiles("dynamodb")
@EnabledIfEnvironmentVariable(named = "DYNAMODB_REHEARSAL_SOURCE", matches = ".+")
class DynamoMigrationCompatibilityTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry props) {
        String endpoint = System.getenv("DYNAMODB_TEST_ENDPOINT");
        if (endpoint == null || !Set.of("127.0.0.1", "localhost").contains(java.net.URI.create(endpoint).getHost())) throw new IllegalArgumentException("Solo ensayo local");
        props.add("app.dynamodb.endpoint", () -> endpoint);
        props.add("app.dynamodb.table", () -> System.getenv("DYNAMODB_REHEARSAL_TABLE"));
    }
    @Autowired ObjectMapper json;
    @Autowired ProjectDataPort projects;
    @Autowired ProjectMemberDataPort members;
    @Autowired UmlDiagramDataPort diagrams;
    @Autowired UserDataPort users;
    @Autowired UserStore userStore;
    @Autowired DepartamentoStore departments;
    @Autowired CollaborationSessionStore sessions;
    @Autowired UmlCollaborationActivityDataPort activities;
    @Test void offlineMigrationPreservesAllDomainFieldsAndLookups() throws Exception {
        var source = json.readTree(Files.readAllBytes(Path.of(System.getenv("DYNAMODB_REHEARSAL_SOURCE"))));
        assertThat(projects.count()).isEqualTo(source.get("PROJECT").size());
        assertThat(users.count()).isEqualTo(source.get("USER").size());
        assertThat(diagrams.count()).isEqualTo(source.get("DIAGRAM").size());
        assertThat(members.count()).isEqualTo(source.get("MEMBER").size());
        for (var value : source.get("PROJECT")) {
            var expected = json.treeToValue(value, ProjectEntity.class);
            assertThat(projects.findById(expected.getId()).orElseThrow()).usingRecursiveComparison().isEqualTo(expected);
        }
        for (var value : source.get("USER")) {
            var expected = json.treeToValue(value, UserEntity.class);
            assertThat(users.findByUsernameIgnoreCase(expected.getUsername()).orElseThrow()).usingRecursiveComparison().isEqualTo(expected);
            assertThat(userStore.findByUsername(expected.getUsername()).orElseThrow().getPassword()).isEqualTo(expected.getPasswordHash());
        }
        for (var value : source.get("DIAGRAM")) {
            var expected = json.treeToValue(value, UmlDiagramEntity.class);
            assertThat(diagrams.findByProjectId(expected.getProjectId()).orElseThrow()).usingRecursiveComparison().isEqualTo(expected);
        }
        for (var value : source.get("MEMBER")) {
            var expected = json.treeToValue(value, ProjectMemberEntity.class);
            assertThat(members.findById(expected.getId()).orElseThrow()).usingRecursiveComparison().isEqualTo(expected);
        }
        for (var value : source.get("DEPARTMENT")) {
            var expected = json.treeToValue(value, Departamento.class);
            assertThat(departments.findByNombreIgnoreCase(expected.getNombre()).orElseThrow()).usingRecursiveComparison().isEqualTo(expected);
        }
        for (var value : source.get("SESSION")) {
            var expected = json.treeToValue(value, UmlCollaborationSession.class);
            assertThat(sessions.findById(expected.getId()).orElseThrow()).usingRecursiveComparison().isEqualTo(expected);
        }
        for (var value : source.get("ACTIVITY")) {
            var expected = json.treeToValue(value, UmlCollaborationActivityEntity.class);
            assertThat(activities.findById(expected.getEventId()).orElseThrow()).usingRecursiveComparison().isEqualTo(expected);
        }
    }
}
