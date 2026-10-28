package com.arqnova.modelo.entidades;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectEntity {

    private String id;

    private String name;

    private String description;

    private ProjectStatus status;

    private String ownerUserId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
