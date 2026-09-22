package com.arqnova.controller;

import com.arqnova.dto.request.UmlBackendGenerationRequest;
import com.arqnova.service.UmlBackendGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/uml-backend")
@RequiredArgsConstructor
public class UmlBackendGeneratorController {
    private final UmlBackendGeneratorService service;

    @PostMapping(value = "/generate", produces = "application/zip")
    public ResponseEntity<byte[]> generate(@RequestBody UmlBackendGenerationRequest request) {
        var generated = service.generate(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + generated.fileName() + "\"")
                .header("X-Generated-Classes", String.valueOf(generated.classCount()))
                .header("X-Generated-Relations", String.valueOf(generated.relationCount()))
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(generated.content().length)
                .body(generated.content());
    }

    @PostMapping(value = "/postman", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> generatePostman(@RequestBody UmlBackendGenerationRequest request) {
        var generated = service.generatePostman(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + generated.fileName() + "\"")
                .header("X-Postman-Entities", String.valueOf(generated.entityCount()))
                .header("X-Postman-Requests", String.valueOf(generated.requestCount()))
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(generated.content().length)
                .body(generated.content());
    }
}
