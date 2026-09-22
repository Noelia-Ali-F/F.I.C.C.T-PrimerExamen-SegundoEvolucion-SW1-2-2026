package com.arqnova.service;

import com.arqnova.domain.model.UmlCollaborationSession;
import com.arqnova.dto.request.CreateUmlSessionRequest;
import com.arqnova.dto.request.UmlCollaborationEvent;
import com.arqnova.dto.response.UmlSessionResponse;
import com.arqnova.exception.UnauthorizedActionException;
import com.arqnova.persistence.CollaborationSessionStore;
import com.arqnova.persistence.jpa.entity.ProjectMemberRole;
import com.arqnova.persistence.jpa.repository.UmlDiagramJpaRepository;
import com.arqnova.persistence.jpa.repository.UmlCollaborationActivityJpaRepository;
import com.arqnova.persistence.jpa.entity.UmlCollaborationActivityEntity;
import com.arqnova.persistence.jpa.service.UmlProjectPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UmlCollaborationService {
    private final CollaborationSessionStore store;
    private final UmlProjectPersistenceService projects;
    private final UmlDiagramJpaRepository diagrams;
    private final UmlCollaborationActivityJpaRepository activities;

    public UmlSessionResponse start(CreateUmlSessionRequest request, String username) {
        projects.requireAccess(request.getProjectId(), username, ProjectMemberRole.OWNER);
        UmlCollaborationSession session = store.findActiveByProjectId(request.getProjectId())
                .orElseGet(() -> UmlCollaborationSession.builder()
                        .id(UUID.randomUUID().toString())
                        .projectId(request.getProjectId())
                        .inviteCode(randomCode())
                        .createdBy(username)
                        .inviteRole(ProjectMemberRole.EDITOR.name())
                        .active(true)
                        .createdAt(LocalDateTime.now())
                        .build());
        session.setProjectName(request.getProjectName());
        session.setInviteRole(inviteRole(request.getInviteRole()).name());
        session.setModel(canonicalModel(request.getProjectId(), request.getModel()));
        session.setUpdatedAt(LocalDateTime.now());
        return response(store.save(session), ProjectMemberRole.OWNER);
    }

    public UmlSessionResponse join(String inviteCode, String username) {
        UmlCollaborationSession session = store.findActiveByInviteCode(inviteCode.trim().toUpperCase())
                .orElseThrow(() -> new UnauthorizedActionException("La invitación no existe o ya no está activa"));
        ProjectMemberRole acceptedRole = "VIEWER".equals(session.getInviteRole()) ? ProjectMemberRole.VIEWER : ProjectMemberRole.EDITOR;
        var membership = projects.acceptInvitation(session.getProjectId(), username, acceptedRole);
        session.setInviteRole(membership.getMemberRole().name());
        session.setModel(canonicalModel(session.getProjectId(), session.getModel()));
        return response(session, membership.getMemberRole());
    }

    public UmlCollaborationSession requireSession(String sessionId, String projectId) {
        UmlCollaborationSession session = store.findById(sessionId)
                .filter(UmlCollaborationSession::isActive)
                .orElseThrow(() -> new UnauthorizedActionException("La sesión colaborativa no está activa"));
        if (!session.getProjectId().equals(projectId)) throw new UnauthorizedActionException("La sesión no pertenece al proyecto indicado");
        return session;
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public synchronized void apply(UmlCollaborationEvent event) {
        if (event.getType() == null || event.getType().startsWith("participant:") || event.getType().startsWith("session:")) return;
        UmlCollaborationSession session = requireSession(event.getSessionId(), event.getProjectId());
        Map<String, Object> model = canonicalModel(session.getProjectId(), session.getModel());
        List<Map<String, Object>> classes = (List<Map<String, Object>>) model.computeIfAbsent("classes", key -> new ArrayList<>());
        List<Map<String, Object>> relations = (List<Map<String, Object>>) model.computeIfAbsent("relations", key -> new ArrayList<>());
        Map<String, Object> element = event.getData() == null ? null : (Map<String, Object>) event.getData().get("element");

        switch (event.getType()) {
            case "class:create" -> upsert(classes, element);
            case "class:update", "class:move" -> upsert(classes, element);
            case "attribute:create" -> createAttribute(classes, event);
            case "attribute:update" -> updateAttribute(classes, event);
            case "attribute:delete" -> deleteAttribute(classes, event);
            case "class:delete" -> {
                classes.removeIf(item -> event.getElementId().equals(item.get("id")));
                relations.removeIf(item -> event.getElementId().equals(item.get("sourceId")) || event.getElementId().equals(item.get("targetId")));
            }
            case "relation:create", "relation:update" -> upsert(relations, element);
            case "relation:delete" -> relations.removeIf(item -> event.getElementId().equals(item.get("id")));
            default -> throw new IllegalArgumentException("Evento UML no soportado: " + event.getType());
        }
        model.put("updatedAt", LocalDateTime.now().toString());
        session.setModel(model); // snapshot transitorio; el modelo canónico permanece en uml_diagrams
        session.setUpdatedAt(LocalDateTime.now());
        var diagram = diagrams.findByProjectIdForUpdate(session.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("El proyecto no tiene un diagrama UML canónico"));
        diagram.setModel(model);
        diagram.setModelVersion(diagram.getModelVersion() + 1);
        diagrams.save(diagram);
        store.save(session);
        recordActivity(event);
    }

    @Transactional
    public void recordActivity(UmlCollaborationEvent event) {
        if (!isTraceable(event) || activities.existsById(event.getEventId())) return;
        long timestamp = event.getTimestamp() == null ? System.currentTimeMillis() : event.getTimestamp();
        activities.save(UmlCollaborationActivityEntity.builder()
                .eventId(event.getEventId()).sessionId(event.getSessionId()).projectId(event.getProjectId())
                .userId(event.getUserId()).userName(event.getUserName()).eventType(event.getType())
                .elementId(event.getElementId()).elementType(event.getElementType()).operation(event.getOperation())
                .eventData(event.getData()).occurredAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()))
                .build());
    }

    @Transactional(readOnly = true)
    public List<UmlCollaborationEvent> activities(String sessionId, String username) {
        UmlCollaborationSession session = store.findById(sessionId)
                .orElseThrow(() -> new UnauthorizedActionException("La sesión colaborativa no existe"));
        projects.requireAccess(session.getProjectId(), username);
        return activities.findTop100BySessionIdOrderByOccurredAtDesc(sessionId).stream().map(item ->
                UmlCollaborationEvent.builder().eventId(item.getEventId()).sessionId(item.getSessionId())
                        .projectId(item.getProjectId()).userId(item.getUserId()).userName(item.getUserName())
                        .type(item.getEventType()).elementId(item.getElementId()).elementType(item.getElementType())
                        .operation(item.getOperation()).data(item.getEventData())
                        .timestamp(item.getOccurredAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()).build()
        ).toList();
    }

    private boolean isTraceable(UmlCollaborationEvent event) {
        if (event == null || event.getEventId() == null || event.getSessionId() == null || event.getProjectId() == null
                || event.getUserId() == null || event.getUserName() == null || event.getType() == null
                || event.getOperation() == null) return false;
        return event.getType().equals("session:join") || event.getType().equals("session:leave")
                || event.getType().equals("class:create") || event.getType().equals("class:update")
                || event.getType().equals("class:delete") || event.getType().equals("class:move")
                || event.getType().equals("attribute:create") || event.getType().equals("attribute:update")
                || event.getType().equals("attribute:delete") || event.getType().equals("relation:create")
                || event.getType().equals("relation:update") || event.getType().equals("relation:delete");
    }

    @SuppressWarnings("unchecked")
    private void createAttribute(List<Map<String, Object>> classes, UmlCollaborationEvent event) {
        Map<String, Object> data = requireAttributeData(event);
        Map<String, Object> owner = findClass(classes, String.valueOf(data.get("classId")));
        List<Map<String, Object>> attributes = attributesOf(owner);
        Map<String, Object> attribute = (Map<String, Object>) data.get("attribute");
        String attributeId = String.valueOf(data.get("attributeId"));
        if (attribute == null || attribute.get("id") == null || !attributeId.equals(String.valueOf(attribute.get("id"))))
            throw new IllegalArgumentException("El atributo nuevo no coincide con attributeId");
        if (attributes.stream().anyMatch(item -> attributeId.equals(String.valueOf(item.get("id")))))
            throw new IllegalArgumentException("El atributo ya existe");
        attributes.add(new LinkedHashMap<>(attribute));
    }

    @SuppressWarnings("unchecked")
    private void updateAttribute(List<Map<String, Object>> classes, UmlCollaborationEvent event) {
        Map<String, Object> data = requireAttributeData(event);
        Map<String, Object> owner = findClass(classes, String.valueOf(data.get("classId")));
        List<Map<String, Object>> attributes = attributesOf(owner);
        String attributeId = String.valueOf(data.get("attributeId"));
        Map<String, Object> attribute = (Map<String, Object>) data.get("attribute");
        if (attribute == null || attribute.get("id") == null || !attributeId.equals(String.valueOf(attribute.get("id"))))
            throw new IllegalArgumentException("El atributo actualizado no coincide con attributeId");
        int index = attributeIndex(attributes, attributeId);
        attributes.set(index, new LinkedHashMap<>(attribute));
    }

    private void deleteAttribute(List<Map<String, Object>> classes, UmlCollaborationEvent event) {
        Map<String, Object> data = requireAttributeData(event);
        Map<String, Object> owner = findClass(classes, String.valueOf(data.get("classId")));
        List<Map<String, Object>> attributes = attributesOf(owner);
        attributes.remove(attributeIndex(attributes, String.valueOf(data.get("attributeId"))));
    }

    private Map<String, Object> requireAttributeData(UmlCollaborationEvent event) {
        Map<String, Object> data = event.getData();
        if (data == null || data.get("classId") == null || data.get("attributeId") == null)
            throw new IllegalArgumentException("La operación de atributo requiere classId y attributeId");
        String attributeId = String.valueOf(data.get("attributeId"));
        if (!attributeId.equals(event.getElementId()))
            throw new IllegalArgumentException("elementId debe coincidir con attributeId");
        return data;
    }

    private Map<String, Object> findClass(List<Map<String, Object>> classes, String classId) {
        return classes.stream().filter(item -> classId.equals(String.valueOf(item.get("id")))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La clase indicada no existe"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> attributesOf(Map<String, Object> owner) {
        Object attributes = owner.get("attributes");
        if (!(attributes instanceof List<?>)) throw new IllegalArgumentException("La clase no contiene una colección de atributos válida");
        return (List<Map<String, Object>>) attributes;
    }

    private int attributeIndex(List<Map<String, Object>> attributes, String attributeId) {
        for (int index = 0; index < attributes.size(); index++)
            if (attributeId.equals(String.valueOf(attributes.get(index).get("id")))) return index;
        throw new IllegalArgumentException("El atributo indicado no existe en la clase");
    }

    private void upsert(List<Map<String, Object>> target, Map<String, Object> element) {
        if (element == null || element.get("id") == null) throw new IllegalArgumentException("El evento no contiene un elemento válido");
        String id = String.valueOf(element.get("id"));
        target.removeIf(item -> id.equals(String.valueOf(item.get("id"))));
        target.add(new LinkedHashMap<>(element));
    }

    private UmlSessionResponse response(UmlCollaborationSession session, ProjectMemberRole memberRole) {
        String projectName = projects.findProjectById(session.getProjectId()).map(p -> p.getName()).orElse(session.getProjectName());
        return UmlSessionResponse.builder().sessionId(session.getId()).projectId(session.getProjectId())
                .projectName(projectName).inviteCode(session.getInviteCode()).hostUserId(session.getCreatedBy())
                .memberRole(memberRole.name())
                .model(canonicalModel(session.getProjectId(), session.getModel())).build();
    }

    private Map<String, Object> canonicalModel(String projectId, Map<String, Object> fallback) {
        return diagrams.findByProjectId(projectId).map(d -> deepCopy(d.getModel())).orElseGet(() -> deepCopy(fallback));
    }

    private ProjectMemberRole inviteRole(String requested) {
        if (requested == null || requested.isBlank()) return ProjectMemberRole.EDITOR;
        try {
            ProjectMemberRole role = ProjectMemberRole.valueOf(requested.trim().toUpperCase());
            if (role == ProjectMemberRole.OWNER) throw new IllegalArgumentException("Una invitación no puede otorgar OWNER");
            return role;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("inviteRole debe ser EDITOR o VIEWER");
        }
    }

    private String randomCode() { return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(); }
    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopy(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : new com.fasterxml.jackson.databind.ObjectMapper().convertValue(value, LinkedHashMap.class);
    }
}
