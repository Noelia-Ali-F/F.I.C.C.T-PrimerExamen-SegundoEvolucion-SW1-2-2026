package com.arqnova.controller;

import com.arqnova.dto.request.LoginRequest;
import com.arqnova.dto.request.RegisterRequest;
import com.arqnova.dto.response.ApiResponse;
import com.arqnova.dto.response.AuthResponse;
import com.arqnova.dto.response.CurrentUserResponse;
import com.arqnova.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Acceso, registro y sesión de usuarios de ArqNova")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Devuelve los datos de sesión y roles del usuario")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/v1/auth/login - Usuario: {}", request.getUsername());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login exitoso", response));
    }

    @PostMapping("/register")
    @Operation(summary = "Registro de usuario", description = "Registra una nueva cuenta de usuario de ArqNova")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /api/v1/auth/register - Usuario: {}", request.getUsername());
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Usuario registrado exitosamente", response));
    }

    @GetMapping("/me")
    @Operation(summary = "Consultar sesión actual", description = "Valida el token y devuelve el usuario autenticado")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me(@RequestHeader("X-Usuario") String username) {
        return ResponseEntity.ok(ApiResponse.ok("Sesión válida", authService.currentUser(username)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión", description = "Confirma el cierre de una sesión autenticada sin estado servidor")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok(ApiResponse.ok("Sesión finalizada"));
    }
}
