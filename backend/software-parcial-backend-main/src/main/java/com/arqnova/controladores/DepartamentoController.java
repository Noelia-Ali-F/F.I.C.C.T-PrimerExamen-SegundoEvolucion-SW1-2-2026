package com.arqnova.controladores;

import com.arqnova.modelo.enums.RolUsuario;
import com.arqnova.modelo.dto.request.CrearDepartamentoRequest;
import com.arqnova.modelo.dto.response.ApiResponse;
import com.arqnova.modelo.dto.response.DepartamentoResponse;
import com.arqnova.servicios.DepartamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller REST para gestión de departamentos.
 * Solo el ADMINISTRADOR puede crear o desactivar departamentos.
 *
 * GET    /api/v1/departamentos          → Listar TODOS incluyendo inactivos (admin view)
 * GET    /api/v1/departamentos/nombres  → Solo nombres activos (selectores)
 * POST   /api/v1/departamentos          → Crear (solo ADMINISTRADOR)
 * DELETE /api/v1/departamentos/{id}     → Smart-delete (solo ADMINISTRADOR):
 *         - Si tiene tareas → soft-delete (activo=false, se oculta de selectores)
 *         - Si no tiene tareas → hard-delete (se elimina de BD)
 * PATCH  /api/v1/departamentos/{id}/reactivar → Reactivar un departamento inactivo (solo ADMINISTRADOR)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/departamentos")
@RequiredArgsConstructor
@Tag(name = "Departamentos", description = "Gestión de departamentos del sistema. CRUD administrativo con soft-delete.")
public class DepartamentoController {

    private final DepartamentoService departamentoService;
    // ─── GET: Listar todos (admin view — incluye inactivos) ───────────────────

    @GetMapping
    @Operation(summary = "Listar departamentos (admin)", description = "Retorna todos los departamentos incluyendo inactivos. Para la vista admin.")
    public ResponseEntity<ApiResponse<List<DepartamentoResponse>>> listarDepartamentos(
            @RequestHeader(value = "X-Usuario", required = false) String usuario,
            @RequestHeader(value = "X-Rol", required = false) RolUsuario rolUsuario
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Departamentos obtenidos", departamentoService.listarDepartamentos(usuario, rolUsuario)));
    }

    // ─── GET: Solo nombres activos (para selectores) ──────────────────────────

    @GetMapping("/nombres")
    @Operation(summary = "Listar nombres activos", description = "Retorna solo los nombres de departamentos ACTIVOS para selectores y catalogos.")
    public ResponseEntity<ApiResponse<List<String>>> listarNombresDepartamentos(
            @RequestHeader(value = "X-Usuario", required = false) String usuario,
            @RequestHeader(value = "X-Rol", required = false) RolUsuario rolUsuario
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Nombres de departamentos activos obtenidos", departamentoService.listarNombresDepartamentos(usuario, rolUsuario)));
    }

    // ─── POST: Crear (solo ADMINISTRADOR) ────────────────────────────────────

    @PostMapping
    @Operation(summary = "Crear departamento", description = "Crea un nuevo departamento. Solo ADMINISTRADOR.")
    public ResponseEntity<ApiResponse<DepartamentoResponse>> crearDepartamento(
            @Valid @RequestBody CrearDepartamentoRequest request,
            @RequestHeader(value = "X-Usuario", required = false) String usuario,
            @RequestHeader(value = "X-Rol", required = false) RolUsuario rolUsuario
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Departamento creado exitosamente", departamentoService.crearDepartamento(request, usuario, rolUsuario)));
    }

    // ─── DELETE: Desactivar (solo ADMINISTRADOR) ────────────────────────────

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Desactivar departamento",
        description = "Desactiva el departamento para que no pueda asignarse a nuevos usuarios. Solo ADMINISTRADOR."
    )
    public ResponseEntity<ApiResponse<Map<String, String>>> eliminarDepartamento(
            @PathVariable String id,
            @RequestHeader(value = "X-Usuario", required = false) String usuario,
            @RequestHeader(value = "X-Rol", required = false) RolUsuario rolUsuario
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Departamento desactivado exitosamente", departamentoService.eliminarDepartamento(id, usuario, rolUsuario)));
    }

    // ─── PATCH: Reactivar (solo ADMINISTRADOR) ────────────────────────────────

    @PatchMapping("/{id}/reactivar")
    @Operation(summary = "Reactivar departamento", description = "Reactiva un departamento previamente desactivado. Solo ADMINISTRADOR.")
    public ResponseEntity<ApiResponse<DepartamentoResponse>> reactivarDepartamento(
            @PathVariable String id,
            @RequestHeader(value = "X-Usuario", required = false) String usuario,
            @RequestHeader(value = "X-Rol", required = false) RolUsuario rolUsuario
    ) {
        DepartamentoResponse reactivado = departamentoService.reactivarDepartamento(id, usuario, rolUsuario);
        return ResponseEntity.ok(ApiResponse.ok("Departamento \"" + reactivado.getNombre() + "\" reactivado exitosamente", reactivado));
    }

    // ─── PUT: Actualizar (solo ADMINISTRADOR) ────────────────────────────────

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar departamento", description = "Actualiza el nombre y descripción de un departamento. Solo ADMINISTRADOR.")
    public ResponseEntity<ApiResponse<DepartamentoResponse>> actualizarDepartamento(
            @PathVariable String id,
            @Valid @RequestBody CrearDepartamentoRequest request,
            @RequestHeader(value = "X-Usuario", required = false) String usuario,
            @RequestHeader(value = "X-Rol", required = false) RolUsuario rolUsuario
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Departamento actualizado exitosamente", departamentoService.actualizarDepartamento(id, request, usuario, rolUsuario)));
    }


}
