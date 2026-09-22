package com.arqnova.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UmlAiCommandResponse {
    private String status;
    private String operation;
    private String targetId;
    private Map<String, Object> parameters;
    private String message;
    private List<UmlAiOperationResponse> operations;
    private String clarificationQuestion;
    private String interpreter;
    private boolean fallbackUsed;
}
