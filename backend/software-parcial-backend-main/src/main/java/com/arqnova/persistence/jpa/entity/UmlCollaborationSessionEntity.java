package com.arqnova.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "uml_collaboration_sessions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UmlCollaborationSessionEntity {
    @Id @Column(length = 64, nullable = false, updatable = false)
    private String id;
    @Column(name = "project_id", length = 128, nullable = false, updatable = false)
    private String projectId;
    @Column(name = "invite_code", length = 100, nullable = false, unique = true)
    private String inviteCode;
    @Column(name = "created_by", length = 64, nullable = false, updatable = false)
    private String createdBy;
    @Enumerated(EnumType.STRING) @Column(name = "invite_role", length = 30, nullable = false)
    private ProjectMemberRole inviteRole;
    @Column(nullable = false)
    private boolean active;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "model_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> modelSnapshot;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist void prePersist() { var now=LocalDateTime.now(); if(inviteRole==null) inviteRole=ProjectMemberRole.EDITOR; if(createdAt==null) createdAt=now; if(updatedAt==null) updatedAt=now; }
    @PreUpdate void preUpdate() { updatedAt=LocalDateTime.now(); }
}
