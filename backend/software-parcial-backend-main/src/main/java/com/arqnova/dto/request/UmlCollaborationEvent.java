package com.arqnova.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UmlCollaborationEvent {
    private String eventId;
    private String type;
    private String projectId;
    private String sessionId;
    private String userId;
    private String userName;
    private String elementId;
    private String elementType;
    private String operation;
    private Map<String, Object> data;
    private Long timestamp;
}
