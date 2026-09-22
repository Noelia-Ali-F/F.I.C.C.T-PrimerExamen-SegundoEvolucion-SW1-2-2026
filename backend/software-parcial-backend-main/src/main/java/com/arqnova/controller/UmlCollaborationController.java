package com.arqnova.controller;

import com.arqnova.dto.request.CreateUmlSessionRequest;
import com.arqnova.dto.response.ApiResponse;
import com.arqnova.dto.response.UmlSessionResponse;
import com.arqnova.service.UmlCollaborationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.security.Principal;
import java.util.List;
import com.arqnova.dto.request.UmlCollaborationEvent;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uml-collaboration")
@RequiredArgsConstructor
public class UmlCollaborationController {
    private final UmlCollaborationService service;

    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<UmlSessionResponse>> start(
            @Valid @RequestBody CreateUmlSessionRequest request,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Sesión colaborativa iniciada", service.start(request, principal.getName())));
    }

    @GetMapping("/invitations/{code}")
    public ResponseEntity<ApiResponse<UmlSessionResponse>> join(@PathVariable String code, Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Invitación válida", service.join(code, principal.getName())));
    }

    @GetMapping("/sessions/{sessionId}/activities")
    public ResponseEntity<ApiResponse<List<UmlCollaborationEvent>>> activities(
            @PathVariable String sessionId, Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Actividad colaborativa obtenida",
                service.activities(sessionId, principal.getName())));
    }
}
