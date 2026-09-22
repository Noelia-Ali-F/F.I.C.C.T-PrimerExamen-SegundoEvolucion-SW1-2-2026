package com.arqnova.controller;

import com.arqnova.dto.request.UmlFlutterGenerationRequest;
import com.arqnova.service.UmlFlutterGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/uml-flutter")
@RequiredArgsConstructor
public class UmlFlutterGeneratorController {
    private final UmlFlutterGeneratorService service;

    @PostMapping(value = "/generate", produces = "application/zip")
    public ResponseEntity<byte[]> generate(@RequestBody UmlFlutterGenerationRequest request) {
        var generated = service.generate(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + generated.fileName() + "\"")
                .header("X-Generated-Models", String.valueOf(generated.modelCount()))
                .header("X-Generated-Screens", String.valueOf(generated.screenCount()))
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(generated.content().length)
                .body(generated.content());
    }
}
