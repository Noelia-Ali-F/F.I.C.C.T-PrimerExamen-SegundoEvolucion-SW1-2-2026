package com.arqnova.servicios;

import com.arqnova.modelo.Usuario;
import com.arqnova.servicios.puertos.salida.UserStore;
import lombok.RequiredArgsConstructor;
import java.util.Optional;

@RequiredArgsConstructor
public class UsuarioConsultaService implements com.arqnova.servicios.puertos.entrada.UserQueryUseCase {
    private final UserStore usuarios;
    public Optional<Usuario> findByUsername(String username) { return usuarios.findByUsername(username); }
}
