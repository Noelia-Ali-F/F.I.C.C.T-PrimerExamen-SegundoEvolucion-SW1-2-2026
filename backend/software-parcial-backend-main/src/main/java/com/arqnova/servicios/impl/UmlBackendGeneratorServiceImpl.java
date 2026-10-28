package com.arqnova.servicios.impl;
import com.arqnova.servicios.UmlBackendGeneratorService;
import com.arqnova.servicios.puertos.salida.BackendGenerationPort;
import com.arqnova.modelo.dto.request.*;
public class UmlBackendGeneratorServiceImpl implements UmlBackendGeneratorService {
 private final BackendGenerationPort generator;
 public UmlBackendGeneratorServiceImpl(BackendGenerationPort generator){this.generator=generator;}
 public GeneratedBackend generate(UmlBackendGenerationRequest request){return generator.generate(request);}
 public GeneratedPostman generatePostman(UmlBackendGenerationRequest request){return generator.generatePostman(request);}
}
