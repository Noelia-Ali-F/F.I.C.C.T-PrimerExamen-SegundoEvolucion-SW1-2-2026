package com.arqnova.service;

import com.arqnova.dto.request.UmlBackendGenerationRequest;

public interface UmlBackendGeneratorService {
    GeneratedBackend generate(UmlBackendGenerationRequest request);
    GeneratedPostman generatePostman(UmlBackendGenerationRequest request);

    record GeneratedBackend(String fileName, byte[] content, int classCount, int relationCount) {}
    record GeneratedPostman(String fileName, byte[] content, int entityCount, int requestCount) {}
}
