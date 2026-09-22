package com.arqnova.dto.project;

import com.arqnova.persistence.jpa.entity.ProjectMemberRole;
import com.arqnova.persistence.jpa.entity.ProjectStatus;

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
