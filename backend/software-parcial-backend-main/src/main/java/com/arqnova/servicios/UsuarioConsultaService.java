package com.arqnova.servicios;

import com.arqnova.modelo.Usuario;
import com.arqnova.repositorios.UserStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UsuarioConsultaService {
    private final UserStore usuarios;
    public Optional<Usuario> findByUsername(String username) { return usuarios.findByUsername(username); }
}
