package com.arqnova.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UmlCollaborationSession {
    @Id
    private String id;
    private String projectId;
    private String projectName;
    private String inviteCode;
    private String createdBy;
    private String inviteRole;
    private boolean active;
    private Map<String, Object> model;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
