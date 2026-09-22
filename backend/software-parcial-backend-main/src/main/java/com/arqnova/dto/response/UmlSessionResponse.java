package com.arqnova.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class UmlSessionResponse {
    private String sessionId;
    private String projectId;
    private String projectName;
    private String inviteCode;
    private String hostUserId;
    private String memberRole;
    private Map<String, Object> model;
}
