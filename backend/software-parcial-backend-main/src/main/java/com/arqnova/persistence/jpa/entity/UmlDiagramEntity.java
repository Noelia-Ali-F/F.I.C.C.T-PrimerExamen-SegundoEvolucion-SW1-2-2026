package com.arqnova.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "uml_diagrams")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UmlDiagramEntity {

    @Id
    @Column(length = 128, nullable = false, updatable = false)
    private String id;

    @Column(name = "project_id", length = 128, nullable = false, unique = true, updatable = false)
    private String projectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> model = new LinkedHashMap<>();

    @Column(name = "model_version", nullable = false)
    @Builder.Default
    private int modelVersion = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (model == null) model = new LinkedHashMap<>();
        if (modelVersion < 1) modelVersion = 1;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
