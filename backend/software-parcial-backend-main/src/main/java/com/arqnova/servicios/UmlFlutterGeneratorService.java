package com.arqnova.servicios;

import com.arqnova.modelo.dto.request.UmlFlutterGenerationRequest;

public interface UmlFlutterGeneratorService {
    GeneratedFlutter generate(UmlFlutterGenerationRequest request);

    record GeneratedFlutter(String fileName, byte[] content, int modelCount, int screenCount) {}
}
