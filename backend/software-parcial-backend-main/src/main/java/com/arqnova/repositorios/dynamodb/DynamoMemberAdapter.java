package com.arqnova.repositorios.dynamodb;

import com.arqnova.modelo.entidades.*;
import com.arqnova.servicios.puertos.salida.ProjectMemberDataPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;

@Repository @Profile("dynamodb")
public class DynamoMemberAdapter extends DynamoEntityAdapter<ProjectMemberEntity, ProjectMemberId> implements ProjectMemberDataPort {
    public DynamoMemberAdapter(DynamoDocumentStore store) { super(store, "MEMBER", ProjectMemberEntity.class, ProjectMemberEntity::getId, DynamoMemberAdapter::memberKey); }
    static String memberKey(ProjectMemberId id) { return compound(id.getProjectId(), id.getUserId()); }
    protected void prepare(ProjectMemberEntity v, ProjectMemberEntity old) {
        store.requireParent("PROJECT", v.getId().getProjectId()); Objects.requireNonNull(v.getMemberRole());
        v.setCreatedAt(old != null ? old.getCreatedAt() : v.getCreatedAt() == null ? LocalDateTime.now() : v.getCreatedAt());
    }
    public long countByIdProjectId(String id) { return findAll().stream().filter(v -> v.getId().getProjectId().equals(id)).count(); }
    public Optional<ProjectMemberEntity> findByIdProjectIdAndIdUserId(String project, String user) { return findById(new ProjectMemberId(project, user)); }
    public List<ProjectMemberEntity> findByIdUserId(String user) { return findAll().stream().filter(v -> v.getId().getUserId().equals(user)).toList(); }
}
