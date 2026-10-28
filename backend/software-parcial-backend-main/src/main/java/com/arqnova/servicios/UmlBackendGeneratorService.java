package com.arqnova.servicios;

import com.arqnova.modelo.dto.request.UmlBackendGenerationRequest;

public interface UmlBackendGeneratorService extends com.arqnova.modelo.generacion.GeneratedArtifacts {
    GeneratedBackend generate(UmlBackendGenerationRequest request);
    GeneratedPostman generatePostman(UmlBackendGenerationRequest request);


}
