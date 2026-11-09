package com.arqnova.controladores;

import com.arqnova.modelo.ia.AiConversation.*;
import com.arqnova.modelo.dto.response.ApiResponse;
import com.arqnova.servicios.puertos.entrada.AiConversationUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/ai")
public class AiConversationController {
    private final AiConversationUseCase service;
    public AiConversationController(AiConversationUseCase service){this.service=service;}
    @GetMapping("/status") public ApiResponse<Map<String,Object>> status(@PathVariable String projectId, Principal principal) {
        var modes = new java.util.ArrayList<>(java.util.List.of(Mode.ASK, Mode.REVIEW, Mode.ADVISE, Mode.MEDIATE));
        if (service.canModel(projectId, principal.getName())) modes.add(Mode.MODEL);
        return ApiResponse.ok("Conversación IA", Map.of("enabled", service.enabled(), "modes", modes));
    }
    @GetMapping("/messages") public ApiResponse<Page> history(@PathVariable String projectId,Principal principal,
            @RequestParam(required=false)String cursor,@RequestParam(defaultValue="20")int limit){
        return ApiResponse.ok("Conversación del proyecto",service.history(projectId,principal.getName(),cursor,limit));
    }
    @GetMapping("/messages/{requestId}") public ApiResponse<Turn> get(@PathVariable String projectId,@PathVariable String requestId,Principal principal){
        return ApiResponse.ok("Interacción IA",service.get(projectId,principal.getName(),requestId));
    }
    @PostMapping("/messages") public ResponseEntity<ApiResponse<Turn>> send(@PathVariable String projectId,Principal principal,@Valid @RequestBody Send request){
        return ResponseEntity.accepted().body(ApiResponse.ok("Solicitud registrada",service.send(projectId,principal.getName(),request.requestId(),request.mode(),request.instruction())));
    }
    public record Send(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,100}")String requestId,@NotNull Mode mode,@NotBlank @Size(max=4000)String instruction){}
}
