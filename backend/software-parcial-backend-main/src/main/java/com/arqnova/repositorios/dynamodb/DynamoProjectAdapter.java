package com.arqnova.repositorios.dynamodb;

import com.arqnova.modelo.entidades.*;
import com.arqnova.servicios.puertos.salida.ProjectDataPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;

@Repository @Profile("dynamodb")
public class DynamoProjectAdapter extends DynamoEntityAdapter<ProjectEntity, String> implements ProjectDataPort {
    public DynamoProjectAdapter(DynamoDocumentStore store) { super(store, "PROJECT", ProjectEntity.class, ProjectEntity::getId, v -> v); }
    static String nameKey(ProjectEntity v) { return compound(v.getOwnerUserId(), v.getName().toLowerCase(Locale.ROOT)); }
    protected void prepare(ProjectEntity v, ProjectEntity old) {
        Objects.requireNonNull(v.getOwnerUserId()); Objects.requireNonNull(v.getName());
        if (old != null && !Objects.equals(old.getOwnerUserId(), v.getOwnerUserId())) throw new IllegalArgumentException("El propietario es inmutable");
        if (old != null && !nameKey(old).equals(nameKey(v))) store.release("project-name", nameKey(old), v.getId());
        store.reserve("project-name", nameKey(v), v.getId());
        if (v.getStatus() == null) v.setStatus(ProjectStatus.ACTIVE);
        v.setCreatedAt(old != null ? old.getCreatedAt() : v.getCreatedAt() == null ? LocalDateTime.now() : v.getCreatedAt());
        if (old != null || v.getUpdatedAt() == null) v.setUpdatedAt(LocalDateTime.now());
    }
    protected void deleting(ProjectEntity p) {
        String id = p.getId();
        store.list("MEMBER", ProjectMemberEntity.class).stream().filter(v -> v.getId().getProjectId().equals(id))
                .forEach(v -> store.delete("MEMBER", DynamoMemberAdapter.memberKey(v.getId())));
        store.list("DIAGRAM", UmlDiagramEntity.class).stream().filter(v -> v.getProjectId().equals(id)).forEach(v -> {
            store.release("diagram-project", id, v.getId()); store.delete("DIAGRAM", v.getId());
        });
        store.list("SESSION", com.arqnova.modelo.UmlCollaborationSession.class).stream().filter(v -> v.getProjectId().equals(id)).forEach(v -> {
            store.release("invite", v.getInviteCode(), v.getId()); store.delete("SESSION", v.getId());
        });
        store.list("ACTIVITY", UmlCollaborationActivityEntity.class).stream().filter(v -> v.getProjectId().equals(id))
                .forEach(v -> store.delete("ACTIVITY", v.getEventId()));
        store.release("project-name", nameKey(p), id);
    }
    public List<ProjectEntity> findAllByOrderByUpdatedAtDesc() { return sorted(findAll()); }
    public List<ProjectEntity> findByIdInOrderByUpdatedAtDesc(Collection<String> ids) { return sorted(ids.stream().distinct().map(this::findById).flatMap(Optional::stream).toList()); }
    private List<ProjectEntity> sorted(List<ProjectEntity> rows) { return rows.stream().sorted(Comparator.comparing(ProjectEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))).toList(); }
    public boolean existsByOwnerUserIdAndNameIgnoreCase(String owner, String name) { return findAll().stream().anyMatch(v -> v.getOwnerUserId().equals(owner) && v.getName().equalsIgnoreCase(name)); }
    public boolean existsByOwnerUserIdAndNameIgnoreCaseAndIdNot(String owner, String name, String id) { return findAll().stream().anyMatch(v -> !v.getId().equals(id) && v.getOwnerUserId().equals(owner) && v.getName().equalsIgnoreCase(name)); }
}
