package com.arqnova.servicios;

import com.arqnova.modelo.dto.request.UmlFlutterGenerationRequest;

public interface UmlFlutterGeneratorService extends com.arqnova.modelo.generacion.GeneratedArtifacts {
    GeneratedFlutter generate(UmlFlutterGenerationRequest request);

}
