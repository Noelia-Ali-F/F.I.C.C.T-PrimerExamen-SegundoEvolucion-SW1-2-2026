package com.arqnova.service;

import com.arqnova.dto.request.UmlFlutterGenerationRequest;

public interface UmlFlutterGeneratorService {
    GeneratedFlutter generate(UmlFlutterGenerationRequest request);

    record GeneratedFlutter(String fileName, byte[] content, int modelCount, int screenCount) {}
}
