package com.arqnova.controladores;
import com.arqnova.modelo.dto.response.ApiResponse;
import com.arqnova.servicios.puertos.entrada.ProjectPushUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;

@RestController @RequestMapping("/api/v1/push")
public class ProjectPushController {
    private final ProjectPushUseCase service;
    public ProjectPushController(ProjectPushUseCase service){this.service=service;}
    @GetMapping("/status") public ApiResponse<Map<String,Boolean>> status(){return ApiResponse.ok("Notificaciones",Map.of("enabled",service.enabled()));}
    @PostMapping("/devices") public ApiResponse<Void> register(Principal user,@Valid @RequestBody Device request){service.register(user.getName(),request.fid());return ApiResponse.ok("Dispositivo registrado");}
    @PostMapping("/devices/remove") public ApiResponse<Void> remove(Principal user,@Valid @RequestBody Device request){service.unregister(user.getName(),request.fid());return ApiResponse.ok("Dispositivo desvinculado");}
    public record Device(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,256}")String fid){}
}
