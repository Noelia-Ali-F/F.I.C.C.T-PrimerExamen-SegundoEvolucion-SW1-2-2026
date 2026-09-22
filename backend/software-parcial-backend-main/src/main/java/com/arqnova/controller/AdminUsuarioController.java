package com.arqnova.controller;

import com.arqnova.domain.enums.RolUsuario;
import com.arqnova.dto.request.ActualizarUsuarioRequest;
import com.arqnova.dto.request.CrearUsuarioRequest;
import com.arqnova.dto.request.ActualizarEstadoUsuarioRequest;
import com.arqnova.dto.response.ApiResponse;
import com.arqnova.dto.response.UsuarioAdminResponse;
import com.arqnova.exception.UnauthorizedActionException;
import com.arqnova.service.AvatarStorageService;
import com.arqnova.service.UsuarioAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/usuarios")
@RequiredArgsConstructor
@Tag(name = "Administración de Usuarios", description = "CRUD de usuarios para el rol ADMINISTRADOR")
public class AdminUsuarioController {

    private final UsuarioAdminService usuarioAdminService;
    private final AvatarStorageService avatarStorageService;

    @GetMapping
    @Operation(summary = "Listar usuarios", description = "Retorna todos los usuarios registrados. Solo ADMINISTRADOR.")
    public ResponseEntity<ApiResponse<List<UsuarioAdminResponse>>> listarUsuarios(
            @RequestHeader(value = "X-Usuario", required = false) String usuario,
            @RequestHeader(value = "X-Rol", required = false) RolUsuario rolUsuario
    ) {
        validarAdministrador(usuario, rolUsuario);

        log.info("GET /api/v1/admin/usuarios - Usuario solicitante: {}", usuario);
        List<UsuarioAdminResponse> usuarios = usuarioAdminService.listarUsuarios();
        return ResponseEntity.ok(ApiResponse.ok("Usuarios obtenidos exitosamente", usuarios));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener usuario", description = "Retorna un usuario por ID. Solo ADMINISTRADOR.")
    public ResponseEntity<ApiResponse<UsuarioAdminResponse>> obtenerUsuarioPorId(
            @PathVariable String id,
            @RequestHeader(value = "X-Usuario", required = false) String usuario,
            @RequestHeader(value = "X-Rol", required = false) RolUsuario rolUsuario
    ) {
        validarAdministrador(usuario, rolUsuario);

        log.info("GET /api/v1/admin/usuarios/{} - Usuario solicitante: {}", id, usuario);
        UsuarioAdminResponse usuarioResponse = usuarioAdminService.obtenerPorId(id);
        return ResponseEntity.ok(ApiResponse.ok("Usuario obtenido exitosamente", usuarioResponse));
    }

    @PostMapping
    @Operation(summary = "Crear usuario", description = "Crea un nuevo usuario. Solo ADMINISTRADOR.")
    public ResponseEntity<ApiResponse<UsuarioAdminResponse>> crearUsuario(
            @Valid @RequestBody CrearUsuarioRequest request,
            @RequestHeader(value = "X-Usuario", required = false) String usuario,
            @RequestHeader(value = "X-Rol", required = false) RolUsuario rolUsuario
    ) {
        validarAdministrador(usuario, rolUsuario);

        log.info("POST /api/v1/admin/usuarios - Creando usuario: {}", request.getUsername());
        UsuarioAdminResponse creado = usuarioAdminService.crearUsuario(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Usuario creado exitosamente", creado));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar usuario", description = "Actualiza datos de un usuario. Solo ADMINISTRADOR.")
    public ResponseEntity<ApiResponse<UsuarioAdminResponse>> actualizarUsuario(
            @PathVariable String id,
            @Valid @RequestBody ActualizarUsuarioRequest request,
            @RequestHeader(value = "X-Usuario", required = false) String usuario,
            @RequestHeader(value = "X-Rol", required = false) RolUsuario rolUsuario
    ) {
        validarAdministrador(usuario, rolUsuario);

        log.info("PUT /api/v1/admin/usuarios/{} - Usuario solicitante: {}", id, usuario);
        UsuarioAdminResponse actualizado = usuarioAdminService.actualizarUsuario(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Usuario actualizado exitosamente", actualizado));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar usuario", description = "Elimina un usuario por ID. Solo ADMINISTRADOR.")
    public ResponseEntity<ApiResponse<Map<String, String>>> eliminarUsuario(
            @PathVariable String id,
            @RequestHeader(value = "X-Usuario", required = false) String usuario,
            @RequestHeader(value = "X-Rol", required = false) RolUsuario rolUsuario
    ) {
        validarAdministrador(usuario, rolUsuario);

        UsuarioAdminResponse usuarioAEliminar = usuarioAdminService.obtenerPorId(id);
        if (usuarioAEliminar.getUsername() != null && usuarioAEliminar.getUsername().equalsIgnoreCase(usuario)) {
            throw new IllegalArgumentException("No puedes eliminar tu propio usuario en sesión");
        }

        usuarioAdminService.eliminarUsuario(id);
        Map<String, String> payload = new HashMap<>();
        payload.put("idEliminado", id);
        payload.put("usernameEliminado", usuarioAEliminar.getUsername());

        return ResponseEntity.ok(ApiResponse.ok("Usuario eliminado exitosamente", payload));
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Habilitar o deshabilitar usuario", description = "Actualiza el acceso de una cuenta. Solo ADMINISTRADOR.")
    public ResponseEntity<ApiResponse<UsuarioAdminResponse>> actualizarEstado(
            @PathVariable String id,
            @Valid @RequestBody ActualizarEstadoUsuarioRequest request,
            @RequestHeader(value = "X-Usuario", required = false) String usuario,
            @RequestHeader(value = "X-Rol", required = false) RolUsuario rolUsuario
    ) {
        validarAdministrador(usuario, rolUsuario);
        UsuarioAdminResponse objetivo = usuarioAdminService.obtenerPorId(id);
        if (objetivo.getUsername() != null && objetivo.getUsername().equalsIgnoreCase(usuario) && !request.getHabilitado()) {
            throw new IllegalArgumentException("No puedes deshabilitar tu propia cuenta en sesión");
        }
        UsuarioAdminResponse actualizado = usuarioAdminService.actualizarEstado(id, request.getHabilitado());
        return ResponseEntity.ok(ApiResponse.ok(
                request.getHabilitado() ? "Usuario habilitado exitosamente" : "Usuario deshabilitado exitosamente",
                actualizado));
    }

    @PostMapping("/{id}/avatar")
    @Operation(summary = "Subir avatar", description = "Sube una foto de perfil para el usuario. Solo ADMINISTRADOR.")
    public ResponseEntity<ApiResponse<UsuarioAdminResponse>> subirAvatar(
            @PathVariable String id,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestHeader(value = "X-Usuario", required = false) String usuario,
            @RequestHeader(value = "X-Rol", required = false) RolUsuario rolUsuario
    ) {
        validarAdministrador(usuario, rolUsuario);

        UsuarioAdminResponse targetUser = usuarioAdminService.obtenerPorId(id);
        UsuarioAdminResponse actualizado = usuarioAdminService.actualizarAvatar(id, archivo, usuario);
        
        log.info("Avatar actualizado para usuario {}", targetUser.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Avatar actualizado exitosamente", actualizado));
    }

    @GetMapping("/avatars/{fileName:.+}")
    @Operation(summary = "Obtener avatar", description = "Descarga un avatar almacenado por el módulo de usuarios.")
    public ResponseEntity<Resource> obtenerAvatar(@PathVariable String fileName) {
        Resource avatar = avatarStorageService.cargarAvatar(fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatarStorageService.tipoContenido(fileName)))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .body(avatar);
    }

    private void validarAdministrador(String usuario, RolUsuario rolUsuario) {
        if (!StringUtils.hasText(usuario) || rolUsuario == null) {
            throw new UnauthorizedActionException(
                    "Debe enviar encabezados de contexto válidos: X-Usuario y X-Rol"
            );
        }

        if (!rolUsuario.puedeAdministrar()) {
            throw new UnauthorizedActionException(rolUsuario.name(), "administrar usuarios");
        }
    }
}
