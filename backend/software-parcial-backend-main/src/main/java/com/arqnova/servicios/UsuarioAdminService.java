package com.arqnova.servicios;

import com.arqnova.modelo.dto.request.ActualizarUsuarioRequest;
import com.arqnova.modelo.dto.request.CrearUsuarioRequest;
import com.arqnova.modelo.dto.response.UsuarioAdminResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UsuarioAdminService {

    List<UsuarioAdminResponse> listarUsuarios();

    UsuarioAdminResponse obtenerPorId(String id);

    UsuarioAdminResponse crearUsuario(CrearUsuarioRequest request);

    UsuarioAdminResponse actualizarUsuario(String id, ActualizarUsuarioRequest request);

    UsuarioAdminResponse actualizarAvatar(String id, MultipartFile avatar, String usuarioResponsable);

    UsuarioAdminResponse actualizarEstado(String id, boolean habilitado);

    void eliminarUsuario(String id);
}
