package com.arqnova.servicios.persistencia;

import com.arqnova.modelo.enums.RolUsuario;
import com.arqnova.modelo.entidades.ProjectEntity;
import com.arqnova.modelo.entidades.ProjectMemberEntity;
import com.arqnova.modelo.entidades.ProjectMemberId;
import com.arqnova.modelo.entidades.ProjectMemberRole;
import com.arqnova.modelo.entidades.ProjectStatus;
import com.arqnova.modelo.entidades.UmlDiagramEntity;
import com.arqnova.servicios.puertos.salida.ProjectDataPort;
import com.arqnova.servicios.puertos.salida.ProjectMemberDataPort;
import com.arqnova.servicios.puertos.salida.UmlDiagramDataPort;
import com.arqnova.servicios.puertos.salida.UserDataPort;
import com.arqnova.modelo.excepciones.DuplicateResourceException;
import com.arqnova.modelo.excepciones.ResourceNotFoundException;
import com.arqnova.modelo.excepciones.UnauthorizedActionException;
import lombok.RequiredArgsConstructor;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.UUID;
import com.arqnova.modelo.entidades.UmlCollaborationActivityEntity;
import com.arqnova.servicios.puertos.salida.UmlCollaborationActivityDataPort;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;

@RequiredArgsConstructor
public class UmlProjectPersistenceService implements com.arqnova.servicios.puertos.entrada.ProjectUseCase {

    private final ProjectDataPort projectRepository;
    private final ProjectMemberDataPort memberRepository;
    private final UmlDiagramDataPort diagramRepository;
    private final UserDataPort userRepository;
    private final UmlCollaborationActivityDataPort activities;
    private final com.arqnova.servicios.puertos.entrada.ElementLockUseCase locks;
    private final com.arqnova.servicios.puertos.salida.CollaborationSessionStore sessions;

    public ProjectEntity createProject(String id, String name, String description, String ownerUserId) {
        requireText(id, "projectId");
        requireText(name, "name");
        requireText(ownerUserId, "ownerUserId");
        if (projectRepository.existsById(id)) {
            throw new DuplicateResourceException("Ya existe un proyecto con el ID indicado");
        }
        String normalizedName = name.trim();
        if (projectRepository.existsByOwnerUserIdAndNameIgnoreCase(ownerUserId, normalizedName)) {
            throw new DuplicateResourceException("Ya existe un proyecto UML con ese nombre");
        }

        ProjectEntity project = projectRepository.save(ProjectEntity.builder()
                .id(id)
                .name(normalizedName)
                .description(description == null ? null : description.trim())
                .status(ProjectStatus.ACTIVE)
                .ownerUserId(ownerUserId)
                .build());

        ProjectMemberId ownerId = new ProjectMemberId(id, ownerUserId);
        if (!memberRepository.existsById(ownerId)) {
            memberRepository.save(ProjectMemberEntity.builder()
                    .id(ownerId)
                    .memberRole(ProjectMemberRole.OWNER)
                    .build());
        }
        return project;
    }

    public Optional<ProjectEntity> findProjectById(String projectId) {
        return projectRepository.findById(projectId);
    }

    public UmlDiagramEntity saveDiagram(String diagramId, String projectId, Map<String, Object> model) {
        return saveDiagram(diagramId, projectId, model, null);
    }

    public UmlDiagramEntity saveDiagram(String diagramId, String projectId, Map<String, Object> model,
                                        Integer expectedVersion) {
        requireText(diagramId, "diagramId");
        requireText(projectId, "projectId");
        if (model == null) throw new IllegalArgumentException("model es obligatorio");
        if (!projectRepository.existsById(projectId)) {
            throw new IllegalArgumentException("El proyecto indicado no existe");
        }

        UmlDiagramEntity diagram = diagramRepository.findByProjectIdForUpdate(projectId)
                .map(existing -> {
                    if (expectedVersion != null && expectedVersion != existing.getModelVersion()) {
                        throw new DuplicateResourceException("El diagrama fue actualizado por otra sesión. Recarga antes de guardar");
                    }
                    existing.setModel(new LinkedHashMap<>(model));
                    existing.setModelVersion(existing.getModelVersion() + 1);
                    return existing;
                })
                .orElseGet(() -> UmlDiagramEntity.builder()
                        .id(diagramId)
                        .projectId(projectId)
                        .model(new LinkedHashMap<>(model))
                        .modelVersion(1)
                        .build());
        if (expectedVersion != null && diagram.getModelVersion() == 1 && expectedVersion != 0) {
            throw new DuplicateResourceException("La versión inicial esperada del diagrama debe ser 0");
        }
        ProjectEntity project = projectRepository.findById(projectId).orElseThrow();
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(project);
        return diagramRepository.save(diagram);
    }

    @Override
    public UmlDiagramEntity saveDiagramFromRest(String diagramId, String projectId, Map<String,Object> model,
                                               int expectedVersion, String actor) {
        requireAccess(projectId, actor, ProjectMemberRole.OWNER, ProjectMemberRole.EDITOR);
        var previous = diagramRepository.findByProjectIdForUpdate(projectId)
                .map(UmlDiagramEntity::getModel).orElse(Map.of());
        var planner=new com.arqnova.servicios.impl.UmlMutationPlanner();
        planner.validate(model);
        var affected=planner.affected(previous,model);
        sessions.findActiveByProjectId(projectId).ifPresent(session->{
            for(var element:affected)locks.find(session.getId(),element.type(),element.id()).ifPresent(lock->{
                if(!actor.equals(lock.userId()))throw new DuplicateResourceException("Hay un elemento afectado en edición por otro usuario");
            });
        });
        var saved = saveDiagram(diagramId, projectId, model, expectedVersion);
        String name = userRepository.findByUsernameIgnoreCase(actor).map(user -> user.getFullName())
                .filter(value -> !value.isBlank()).orElse(actor);
        LocalDateTime at = LocalDateTime.now();
        recordChanges(projectId, actor, name, at, "class", previous.get("classes"), saved.getModel().get("classes"));
        recordChanges(projectId, actor, name, at, "relation", previous.get("relations"), saved.getModel().get("relations"));
        var beforeClasses = indexElements(previous.get("classes"));
        var afterClasses = indexElements(saved.getModel().get("classes"));
        var classIds = new LinkedHashSet<>(beforeClasses.keySet());
        classIds.addAll(afterClasses.keySet());
        for (String id : classIds) {
            var before = beforeClasses.getOrDefault(id, Map.of());
            var after = afterClasses.getOrDefault(id, Map.of());
            recordChanges(projectId, actor, name, at, "attribute", before.get("attributes"), after.get("attributes"));
            recordChanges(projectId, actor, name, at, "method", before.get("methods"), after.get("methods"));
        }
        return saved;
    }

    private void recordChanges(String projectId, String actor, String name, LocalDateTime at,
                               String type, Object before, Object after) {
        recordChanges(projectId,actor,name,at,type,before,after,Map.of("origin","REST"));
    }
    @Override public void recordAiChanges(String projectId,Map<String,Object> before,Map<String,Object> after,String actor,String requestId,String provider){
        requireAccess(projectId,actor,ProjectMemberRole.OWNER,ProjectMemberRole.EDITOR);
        String name=userRepository.findByUsernameIgnoreCase(actor).map(user->user.getFullName()).filter(value->value!=null&&!value.isBlank()).orElse(actor);
        var at=LocalDateTime.now();var metadata=Map.<String,Object>of("origin","AI","executor","AI","requestId",requestId,"provider",provider==null?"UNKNOWN":provider);
        recordChanges(projectId,actor,name,at,"class",before.get("classes"),after.get("classes"),metadata);
        recordChanges(projectId,actor,name,at,"relation",before.get("relations"),after.get("relations"),metadata);
        var b=indexElements(before.get("classes"));var a=indexElements(after.get("classes"));var ids=new LinkedHashSet<>(b.keySet());ids.addAll(a.keySet());
        for(String id:ids){var extra=new LinkedHashMap<>(metadata);extra.put("classId",id);
            for(String type:List.of("attribute","method")){String key=type.equals("attribute")?"attributes":"methods";
                recordChanges(projectId,actor,name,at,type,b.getOrDefault(id,Map.of()).get(key),a.getOrDefault(id,Map.of()).get(key),extra);
            }
        }
    }
    private void recordChanges(String projectId,String actor,String name,LocalDateTime at,String type,Object before,Object after,Map<String,Object> metadata) {
        var previous = indexElements(before);
        var current = indexElements(after);
        var ids = new LinkedHashSet<>(previous.keySet());
        ids.addAll(current.keySet());
        for (String id : ids) {
            var old = previous.get(id);
            var next = current.get(id);
            if (Objects.equals(old, next)) continue;
            String action = old == null ? "create" : next == null ? "delete" : "update";
            var element = next == null ? old : next;
            Object label = element.get(type.equals("relation") ? "label" : "name");
            var data = new LinkedHashMap<String,Object>(metadata);
            if (label instanceof String text && !text.isBlank()) data.put("elementName", text.substring(0, Math.min(120, text.length())));
            String operation = switch (action) {
                case "create" -> "Creó";
                case "delete" -> "Eliminó";
                default -> "Actualizó";
            };
            String eventId=metadata.containsKey("requestId")?UUID.nameUUIDFromBytes((projectId+"|"+metadata.get("requestId")+"|"+metadata.getOrDefault("classId","")+"|"+type+"|"+id+"|"+action).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString():UUID.randomUUID().toString();
            activities.save(UmlCollaborationActivityEntity.builder().eventId(eventId)
                    .projectId(projectId).userId(actor).userName(name.substring(0, Math.min(180, name.length())))
                    .occurredAt(at).elementId(id).elementType(type).eventType(type + ":" + action)
                    .operation(operation + switch (type) { case "class" -> " una clase"; case "attribute" -> " un atributo"; case "method" -> " un método"; default -> " una relación"; })
                    .eventData(data).build());
        }
    }

    private Map<String,Map<?,?>> indexElements(Object value) {
        var index = new LinkedHashMap<String,Map<?,?>>();
        if (value instanceof List<?> rows) {
            for (var item : rows) if (item instanceof Map<?,?> row && row.get("id") instanceof String id
                    && !id.isBlank() && id.length() <= 128) index.put(id, row);
        }
        return index;
    }

    public Optional<UmlDiagramEntity> findDiagramByProjectId(String projectId) {
        return diagramRepository.findByProjectId(projectId);
    }

    public ProjectMemberEntity addMember(String projectId, String userId, ProjectMemberRole role) {
        requireText(projectId, "projectId");
        requireText(userId, "userId");
        if (role == null) throw new IllegalArgumentException("memberRole es obligatorio");
        if (!projectRepository.existsById(projectId)) {
            throw new IllegalArgumentException("El proyecto indicado no existe");
        }

        ProjectMemberId id = new ProjectMemberId(projectId, userId);
        return memberRepository.findById(id)
                .map(existing -> {
                    existing.setMemberRole(role);
                    return memberRepository.save(existing);
                })
                .orElseGet(() -> memberRepository.save(ProjectMemberEntity.builder()
                        .id(id)
                        .memberRole(role)
                        .build()));
    }

    public ProjectMemberEntity acceptInvitation(String projectId, String userId, ProjectMemberRole invitedRole) {
        ProjectMemberId id = new ProjectMemberId(projectId, userId);
        return memberRepository.findById(id)
                .map(existing -> {
                    // Una invitación nunca reduce permisos ya concedidos ni puede alterar al OWNER.
                    if (existing.getMemberRole() == ProjectMemberRole.OWNER
                            || (existing.getMemberRole() == ProjectMemberRole.EDITOR && invitedRole == ProjectMemberRole.VIEWER)) {
                        return existing;
                    }
                    existing.setMemberRole(invitedRole);
                    return memberRepository.save(existing);
                })
                .orElseGet(() -> addMember(projectId, userId, invitedRole));
    }

    public void deleteProject(String projectId) {
        if (projectRepository.existsById(projectId)) {
            projectRepository.deleteById(projectId);
            projectRepository.flush();
        }
    }

    public List<ProjectEntity> listAccessible(String userId) {
        if (isAdministrator(userId)) {
            return projectRepository.findAllByOrderByUpdatedAtDesc();
        }
        List<String> projectIds = memberRepository.findByIdUserId(userId).stream()
                .map(member -> member.getId().getProjectId()).toList();
        return projectIds.isEmpty() ? List.of() : projectRepository.findByIdInOrderByUpdatedAtDesc(projectIds);
    }

    public ProjectMemberRole requireAccess(String projectId, String userId, ProjectMemberRole... allowed) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Proyecto", "id", projectId);
        }
        if (isAdministrator(userId)) {
            return ProjectMemberRole.OWNER;
        }
        ProjectMemberRole role = memberRepository.findByIdProjectIdAndIdUserId(projectId, userId)
                .map(ProjectMemberEntity::getMemberRole)
                .orElseThrow(() -> new UnauthorizedActionException("No tienes acceso a este proyecto"));
        if (allowed.length > 0 && !Set.of(allowed).contains(role)) {
            throw new UnauthorizedActionException("Tu membresía no permite realizar esta operación");
        }
        return role;
    }

    public ProjectEntity updateProject(String projectId, String userId, String name, String description,
                                       ProjectStatus status) {
        requireAccess(projectId, userId, ProjectMemberRole.OWNER);
        ProjectEntity project = projectRepository.findById(projectId).orElseThrow();
        if (name != null) {
            requireText(name, "name");
            String normalizedName = name.trim();
            if (projectRepository.existsByOwnerUserIdAndNameIgnoreCaseAndIdNot(
                    project.getOwnerUserId(), normalizedName, projectId)) {
                throw new DuplicateResourceException("Ya existe un proyecto UML con ese nombre");
            }
            project.setName(normalizedName);
        }
        if (description != null) project.setDescription(description.trim());
        if (status != null) project.setStatus(status);
        return projectRepository.save(project);
    }

    public void deleteProjectAuthorized(String projectId, String userId) {
        requireAccess(projectId, userId, ProjectMemberRole.OWNER);
        projectRepository.deleteById(projectId);
        projectRepository.flush();
    }

    private boolean isAdministrator(String userId) {
        return userRepository.findByUsernameIgnoreCase(userId)
                .map(user -> user.getRole() == RolUsuario.ADMINISTRADOR)
                .orElse(false);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " es obligatorio");
        }
    }
}
