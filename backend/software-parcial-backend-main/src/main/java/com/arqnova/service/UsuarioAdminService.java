package com.arqnova.service;

import com.arqnova.dto.request.ActualizarUsuarioRequest;
import com.arqnova.dto.request.CrearUsuarioRequest;
import com.arqnova.dto.response.UsuarioAdminResponse;
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
