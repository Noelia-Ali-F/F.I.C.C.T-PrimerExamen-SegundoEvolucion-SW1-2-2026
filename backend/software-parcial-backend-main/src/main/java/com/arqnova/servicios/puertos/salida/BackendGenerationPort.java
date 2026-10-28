package com.arqnova.servicios.puertos.salida;

import com.arqnova.modelo.dto.request.UmlBackendGenerationRequest;

public interface BackendGenerationPort extends com.arqnova.modelo.generacion.GeneratedArtifacts {
    GeneratedBackend generate(UmlBackendGenerationRequest request);
    GeneratedPostman generatePostman(UmlBackendGenerationRequest request);


}
