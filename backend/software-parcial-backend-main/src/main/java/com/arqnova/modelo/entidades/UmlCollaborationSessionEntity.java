package com.arqnova.modelo.entidades;

import lombok.*;
import java.time.LocalDateTime;
import java.util.Map;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UmlCollaborationSessionEntity {

    private String id;

    private String projectId;

    private String inviteCode;

    private String createdBy;

    private ProjectMemberRole inviteRole;

    private boolean active;

    private Map<String, Object> modelSnapshot;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
