package com.arqnova.dto.project;

import java.util.Map;

public record DiagramResponse(String projectId, int modelVersion, Map<String, Object> model) {
}
