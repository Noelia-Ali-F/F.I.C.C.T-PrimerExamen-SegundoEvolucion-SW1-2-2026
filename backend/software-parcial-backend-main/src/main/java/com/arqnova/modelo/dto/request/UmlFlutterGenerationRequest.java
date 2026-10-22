package com.arqnova.modelo.dto.request;

import com.arqnova.modelo.dto.request.UmlBackendGenerationRequest.UmlModel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UmlFlutterGenerationRequest(
        String projectName,
        String backendBaseUrl,
        UmlModel model
) {}
