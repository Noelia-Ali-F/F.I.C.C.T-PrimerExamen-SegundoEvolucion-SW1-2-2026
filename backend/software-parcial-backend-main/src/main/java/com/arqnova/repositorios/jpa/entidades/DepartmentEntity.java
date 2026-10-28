package com.arqnova.repositorios.jpa.entidades;

import com.arqnova.modelo.entidades.ProjectStatus;
import com.arqnova.modelo.entidades.ProjectMemberRole;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "departments")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DepartmentEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "name", length = 150, nullable = false, unique = true) private String name;
    @Column(name = "description") private String description;
    @Column(name = "created_by", length = 160) private String createdBy;
    @Column(name = "active", nullable = false) private boolean active;
    @Column(name = "created_at") private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
