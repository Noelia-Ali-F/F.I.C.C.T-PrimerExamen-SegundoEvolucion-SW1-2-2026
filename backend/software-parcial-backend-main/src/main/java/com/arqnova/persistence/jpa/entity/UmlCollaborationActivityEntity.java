package com.arqnova.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "uml_collaboration_activities")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UmlCollaborationActivityEntity {
    @Id @Column(name = "event_id", length = 64, nullable = false, updatable = false)
    private String eventId;
    @Column(name = "session_id", length = 64, nullable = false, updatable = false)
    private String sessionId;
    @Column(name = "project_id", length = 128, nullable = false, updatable = false)
    private String projectId;
    @Column(name = "user_id", length = 64, nullable = false, updatable = false)
    private String userId;
    @Column(name = "user_name", length = 180, nullable = false, updatable = false)
    private String userName;
    @Column(name = "event_type", length = 60, nullable = false, updatable = false)
    private String eventType;
    @Column(name = "element_id", length = 128, updatable = false)
    private String elementId;
    @Column(name = "element_type", length = 30, updatable = false)
    private String elementType;
    @Column(length = 255, nullable = false, updatable = false)
    private String operation;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "event_data", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> eventData;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;
}

