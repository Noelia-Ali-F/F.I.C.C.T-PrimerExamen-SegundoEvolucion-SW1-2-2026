package com.arqnova.servicios.puertos.salida;

import com.arqnova.modelo.dto.request.UmlFlutterGenerationRequest;

public interface FlutterGenerationPort extends com.arqnova.modelo.generacion.GeneratedArtifacts {
    GeneratedFlutter generate(UmlFlutterGenerationRequest request);

}
