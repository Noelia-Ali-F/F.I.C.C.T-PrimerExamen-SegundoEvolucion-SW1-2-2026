package com.arqnova.modelo.dto.project;

import com.arqnova.modelo.entidades.ProjectMemberRole;
import com.arqnova.modelo.entidades.ProjectStatus;

import java.time.LocalDateTime;

public record ProjectResponse(
        String id,
        String name,
        String description,
        ProjectStatus status,
        String owner,
        ProjectMemberRole memberRole,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
