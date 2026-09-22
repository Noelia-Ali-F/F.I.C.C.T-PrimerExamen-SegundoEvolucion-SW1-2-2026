package com.arqnova.persistence.jpa.entity;

import com.arqnova.domain.enums.RolUsuario;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="users")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserEntity {
    @Id @Column(length=64, nullable=false, updatable=false) private String id;
    @Column(length=180, nullable=false, unique=true) private String username;
    @Column(name="password_hash", length=255, nullable=false) private String passwordHash;
    @Column(name="full_name", length=200) private String fullName;
    @Enumerated(EnumType.STRING) @Column(length=50, nullable=false) private RolUsuario role;
    @Column(length=150) private String department;
    @Column(name="avatar_url", columnDefinition="text") private String avatarUrl;
    @Column(nullable=false) private boolean enabled;
    @Column(name="created_at", nullable=false, updatable=false) private LocalDateTime createdAt;
    @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt;
    @PrePersist void prePersist(){var now=LocalDateTime.now();if(createdAt==null)createdAt=now;if(updatedAt==null)updatedAt=now;}
    @PreUpdate void preUpdate(){updatedAt=LocalDateTime.now();}
}
