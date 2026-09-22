package com.arqnova.controller;

import com.arqnova.dto.project.CreateProjectRequest;
import com.arqnova.dto.project.DiagramResponse;
import com.arqnova.dto.project.ProjectResponse;
import com.arqnova.dto.project.UpdateProjectRequest;
import com.arqnova.dto.response.ApiResponse;
import com.arqnova.persistence.jpa.entity.ProjectEntity;
import com.arqnova.persistence.jpa.entity.ProjectMemberRole;
import com.arqnova.persistence.jpa.entity.ProjectStatus;
import com.arqnova.persistence.jpa.entity.UmlDiagramEntity;
import com.arqnova.persistence.jpa.service.UmlProjectPersistenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class UmlProjectController {

    private final UmlProjectPersistenceService service;

    @GetMapping
    public ApiResponse<List<ProjectResponse>> list(@RequestHeader("X-Usuario") String username) {
        List<ProjectResponse> projects = service.listAccessible(username).stream()
                .map(project -> response(project, service.requireAccess(project.getId(), username)))
                .toList();
        return ApiResponse.ok("Proyectos UML", projects);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> create(
            @RequestHeader("X-Usuario") String username,
            @Valid @RequestBody CreateProjectRequest request) {
        String id = request.id() == null || request.id().isBlank()
                ? "uml-" + UUID.randomUUID()
                : request.id().trim();
        ProjectEntity project = service.createProject(id, request.name().trim(), trim(request.description()), username);
        if (request.status() != null && !request.status().isBlank()) {
            project = service.updateProject(id, username, null, null, parseStatus(request.status()));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Proyecto creado", response(project, ProjectMemberRole.OWNER)));
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectResponse> get(@PathVariable String projectId,
                                            @RequestHeader("X-Usuario") String username) {
        ProjectMemberRole role = service.requireAccess(projectId, username);
        ProjectEntity project = service.findProjectById(projectId).orElseThrow();
        return ApiResponse.ok("Proyecto UML", response(project, role));
    }

    @PatchMapping("/{projectId}")
    public ApiResponse<ProjectResponse> update(@PathVariable String projectId,
                                               @RequestHeader("X-Usuario") String username,
                                               @Valid @RequestBody UpdateProjectRequest request) {
        ProjectStatus status = request.status() == null ? null : parseStatus(request.status());
        ProjectEntity project = service.updateProject(projectId, username, request.name(), request.description(), status);
        return ApiResponse.ok("Proyecto actualizado", response(project, ProjectMemberRole.OWNER));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@PathVariable String projectId,
                                       @RequestHeader("X-Usuario") String username) {
        service.deleteProjectAuthorized(projectId, username);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/diagram")
    public ApiResponse<DiagramResponse> diagram(@PathVariable String projectId,
                                                @RequestHeader("X-Usuario") String username) {
        service.requireAccess(projectId, username);
        return service.findDiagramByProjectId(projectId)
                .map(value -> ApiResponse.ok("Diagrama UML", diagramResponse(value)))
                .orElseGet(() -> ApiResponse.ok("Diagrama UML vacío",
                        new DiagramResponse(projectId, 0, emptyModel(projectId))));
    }

    @PutMapping("/{projectId}/diagram")
    public ApiResponse<DiagramResponse> saveDiagram(
            @PathVariable String projectId,
            @RequestHeader("X-Usuario") String username,
            @RequestHeader(value = "X-Model-Version", defaultValue = "0") int modelVersion,
            @RequestBody Map<String, Object> model) {
        service.requireAccess(projectId, username, ProjectMemberRole.OWNER, ProjectMemberRole.EDITOR);
        UmlDiagramEntity saved = service.saveDiagram("diagram-" + projectId, projectId, model, modelVersion);
        return ApiResponse.ok("Diagrama guardado", diagramResponse(saved));
    }

    private ProjectResponse response(ProjectEntity project, ProjectMemberRole role) {
        return new ProjectResponse(project.getId(), project.getName(), project.getDescription(), project.getStatus(),
                project.getOwnerUserId(), role, project.getCreatedAt(), project.getUpdatedAt());
    }

    private DiagramResponse diagramResponse(UmlDiagramEntity diagram) {
        return new DiagramResponse(diagram.getProjectId(), diagram.getModelVersion(), diagram.getModel());
    }

    private Map<String, Object> emptyModel(String projectId) {
        ProjectEntity project = service.findProjectById(projectId).orElseThrow();
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("format", "uml-class-diagram");
        model.put("version", 2);
        model.put("title", project.getName());
        model.put("classes", List.of());
        model.put("relations", List.of());
        return model;
    }

    private ProjectStatus parseStatus(String status) {
        try {
            return ProjectStatus.valueOf(status.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Estado de proyecto no permitido");
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
