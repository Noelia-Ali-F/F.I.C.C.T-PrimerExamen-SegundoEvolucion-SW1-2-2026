package com.arqnova.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UmlAiOperationResponse {
    private String type;
    private String targetId;
    private String elementType;
    private Map<String, String> target;
    private Map<String, Object> parameters;
}
