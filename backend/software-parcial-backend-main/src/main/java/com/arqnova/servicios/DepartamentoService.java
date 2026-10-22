package com.arqnova.servicios;

import com.arqnova.modelo.enums.RolUsuario;
import com.arqnova.modelo.Departamento;
import com.arqnova.modelo.dto.request.CrearDepartamentoRequest;
import com.arqnova.modelo.dto.response.DepartamentoResponse;
import com.arqnova.modelo.excepciones.DuplicateResourceException;
import com.arqnova.modelo.excepciones.ResourceNotFoundException;
import com.arqnova.modelo.excepciones.UnauthorizedActionException;
import com.arqnova.repositorios.DepartamentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DepartamentoService {
    private final DepartamentoRepository departamentoRepository;

    public List<DepartamentoResponse> listarDepartamentos(String usuario, RolUsuario rolUsuario) {
        validarContexto(usuario, rolUsuario);

        List<DepartamentoResponse> departamentos = departamentoRepository.findAllByOrderByNombreAsc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return departamentos;
    }

    public List<String> listarNombresDepartamentos(String usuario, RolUsuario rolUsuario) {
        validarContexto(usuario, rolUsuario);

        List<String> nombres = departamentoRepository.findAllByActivoTrueOrderByNombreAsc()
                .stream()
                .map(Departamento::getNombre)
                .collect(Collectors.toList());

        return nombres;
    }

    public DepartamentoResponse crearDepartamento(CrearDepartamentoRequest request, String usuario, RolUsuario rolUsuario) {
        validarAdministrador(usuario, rolUsuario);

        String nombre = request.getNombre().trim();

        // If soft-deleted with same name exists, reactivate instead of creating duplicate
        departamentoRepository.findByNombreIgnoreCase(nombre).ifPresent(existente -> {
            if (!existente.isActivo()) {
                throw new DuplicateResourceException(
                        "Ya existe el departamento '" + nombre + "' (desactivado). " +
                        "Reactivalo desde la tabla en vez de crear uno nuevo."
                );
            }
            throw new DuplicateResourceException("Ya existe un departamento con el nombre: " + nombre);
        });

        Departamento nuevo = Departamento.builder()
                .nombre(nombre)
                .descripcion(StringUtils.hasText(request.getDescripcion()) ? request.getDescripcion().trim() : null)
                .creadoPor(usuario)
                .fechaCreacion(LocalDateTime.now())
                .build();

        Departamento guardado = departamentoRepository.save(nuevo);
        log.info("[Departamentos] Creado: '{}' por {}", guardado.getNombre(), usuario);

        return toResponse(guardado);
    }

    public Map<String,String> eliminarDepartamento(String id, String usuario, RolUsuario rolUsuario) {
        validarAdministrador(usuario, rolUsuario);

        Departamento existente = departamentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Departamento", "id", id));

        Map<String, String> payload = new HashMap<>();
        payload.put("id", id);
        payload.put("nombre", existente.getNombre());
        existente.setActivo(false);
        departamentoRepository.save(existente);

        log.info("[Departamentos] Desactivado: '{}' por {}", existente.getNombre(), usuario);
        payload.put("accion", "DESACTIVADO");
        payload.put("mensaje", "El departamento fue desactivado y ya no aparece en los selectores de usuarios.");

        return payload;
    }

    public DepartamentoResponse reactivarDepartamento(String id, String usuario, RolUsuario rolUsuario) {
        validarAdministrador(usuario, rolUsuario);

        Departamento existente = departamentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Departamento", "id", id));

        if (existente.isActivo()) {
            throw new IllegalStateException("El departamento ya está activo.");
        }

        existente.setActivo(true);
        Departamento reactivado = departamentoRepository.save(existente);
        log.info("[Departamentos] Reactivado: '{}' por {}", reactivado.getNombre(), usuario);

        return toResponse(reactivado);
    }

    public DepartamentoResponse actualizarDepartamento(String id, CrearDepartamentoRequest request, String usuario, RolUsuario rolUsuario) {
        validarAdministrador(usuario, rolUsuario);

        Departamento existente = departamentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Departamento", "id", id));

        String nuevoNombre = request.getNombre().trim();

        // Si el nombre cambia, validar que no exista otro
        if (!existente.getNombre().equalsIgnoreCase(nuevoNombre)) {
            if (departamentoRepository.existsByNombreIgnoreCase(nuevoNombre)) {
                throw new DuplicateResourceException("Ya existe un departamento con el nombre: " + nuevoNombre);
            }
            existente.setNombre(nuevoNombre);
        }

        existente.setDescripcion(StringUtils.hasText(request.getDescripcion()) ? request.getDescripcion().trim() : null);

        Departamento guardado = departamentoRepository.save(existente);
        log.info("[Departamentos] Actualizado: '{}' por {}", guardado.getNombre(), usuario);

        return toResponse(guardado);
    }

    private void validarContexto(String usuario, RolUsuario rolUsuario) {
        if (!StringUtils.hasText(usuario) || rolUsuario == null) {
            throw new UnauthorizedActionException("Debe enviar encabezados de contexto válidos: X-Usuario y X-Rol");
        }
    }

    private void validarAdministrador(String usuario, RolUsuario rolUsuario) {
        validarContexto(usuario, rolUsuario);
        if (!rolUsuario.puedeAdministrar()) {
            throw new UnauthorizedActionException(rolUsuario.name(), "administrar departamentos");
        }
    }

    private DepartamentoResponse toResponse(Departamento d) {
        return DepartamentoResponse.builder()
                .id(d.getId())
                .nombre(d.getNombre())
                .descripcion(d.getDescripcion())
                .creadoPor(d.getCreadoPor())
                .activo(d.isActivo())
                .fechaCreacion(d.getFechaCreacion())
                .build();
    }
}
