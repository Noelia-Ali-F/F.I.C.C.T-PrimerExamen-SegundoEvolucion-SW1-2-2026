package com.arqnova.dto.project;

import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
        @Size(max = 150) String name,
        @Size(max = 4000) String description,
        String status) {
}
