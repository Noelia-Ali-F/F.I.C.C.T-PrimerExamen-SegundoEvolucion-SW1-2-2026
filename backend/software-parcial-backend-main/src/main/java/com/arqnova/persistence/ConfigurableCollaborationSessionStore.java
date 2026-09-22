package com.arqnova.persistence;

import com.arqnova.domain.model.UmlCollaborationSession;
import com.arqnova.persistence.jpa.entity.*;
import com.arqnova.persistence.jpa.repository.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ConfigurableCollaborationSessionStore implements CollaborationSessionStore {
    private final UmlCollaborationSessionJpaRepository postgres;
    public ConfigurableCollaborationSessionStore(UmlCollaborationSessionJpaRepository postgres) {
        this.postgres=postgres;
    }
    public Optional<UmlCollaborationSession> findById(String id) { return postgres.findById(id).map(this::domain); }
    public Optional<UmlCollaborationSession> findActiveByProjectId(String id) { return postgres.findFirstByProjectIdAndActiveTrue(id).map(this::domain); }
    public Optional<UmlCollaborationSession> findActiveByInviteCode(String code) { return postgres.findByInviteCodeAndActiveTrue(code).map(this::domain); }
    public UmlCollaborationSession save(UmlCollaborationSession s) {
        var e=postgres.findById(s.getId()).orElseGet(UmlCollaborationSessionEntity::new);
        e.setId(s.getId()); e.setProjectId(s.getProjectId()); e.setInviteCode(s.getInviteCode()); e.setCreatedBy(s.getCreatedBy());
        e.setInviteRole(role(s.getInviteRole())); e.setActive(s.isActive()); e.setModelSnapshot(s.getModel()); e.setCreatedAt(s.getCreatedAt()); e.setUpdatedAt(s.getUpdatedAt());
        return domain(postgres.save(e));
    }
    private ProjectMemberRole role(String value) { return "VIEWER".equalsIgnoreCase(value) ? ProjectMemberRole.VIEWER : ProjectMemberRole.EDITOR; }
    private UmlCollaborationSession domain(UmlCollaborationSessionEntity e) { return UmlCollaborationSession.builder().id(e.getId()).projectId(e.getProjectId()).inviteCode(e.getInviteCode()).createdBy(e.getCreatedBy()).inviteRole(e.getInviteRole().name()).active(e.isActive()).model(e.getModelSnapshot()).createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt()).build(); }
}
