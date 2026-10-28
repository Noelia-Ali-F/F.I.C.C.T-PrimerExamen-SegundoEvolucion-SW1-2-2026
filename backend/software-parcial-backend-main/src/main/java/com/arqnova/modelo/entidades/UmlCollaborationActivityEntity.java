package com.arqnova.modelo.entidades;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.Map;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UmlCollaborationActivityEntity {

    private String eventId;

    private String sessionId;

    private String projectId;

    private String userId;

    private String userName;

    private String eventType;

    private String elementId;

    private String elementType;

    private String operation;

    private Map<String, Object> eventData;

    private LocalDateTime occurredAt;
}
