package com.arqnova.servicios.impl;
import com.arqnova.servicios.UmlFlutterGeneratorService;
import com.arqnova.servicios.puertos.salida.FlutterGenerationPort;
import com.arqnova.modelo.dto.request.*;
public class UmlFlutterGeneratorServiceImpl implements UmlFlutterGeneratorService {
 private final FlutterGenerationPort generator;
 public UmlFlutterGeneratorServiceImpl(FlutterGenerationPort generator){this.generator=generator;}
 public GeneratedFlutter generate(UmlFlutterGenerationRequest request){return generator.generate(request);}
}
