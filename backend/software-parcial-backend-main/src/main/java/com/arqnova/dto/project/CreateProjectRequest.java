package com.arqnova.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @Size(max = 128) String id,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 4000) String description,
        String status) {
}
