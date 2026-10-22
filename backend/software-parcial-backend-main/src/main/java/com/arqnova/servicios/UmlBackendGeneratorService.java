package com.arqnova.servicios;

import com.arqnova.modelo.dto.request.UmlBackendGenerationRequest;

public interface UmlBackendGeneratorService {
    GeneratedBackend generate(UmlBackendGenerationRequest request);
    GeneratedPostman generatePostman(UmlBackendGenerationRequest request);

    record GeneratedBackend(String fileName, byte[] content, int classCount, int relationCount) {}
    record GeneratedPostman(String fileName, byte[] content, int entityCount, int requestCount) {}
}
