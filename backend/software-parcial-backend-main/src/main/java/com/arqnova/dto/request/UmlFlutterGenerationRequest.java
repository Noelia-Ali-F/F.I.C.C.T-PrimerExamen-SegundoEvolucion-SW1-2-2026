package com.arqnova.dto.request;

import com.arqnova.dto.request.UmlBackendGenerationRequest.UmlModel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UmlFlutterGenerationRequest(
        String projectName,
        String backendBaseUrl,
        UmlModel model
) {}
