package com.arqnova.servicios.impl;

import com.arqnova.modelo.Usuario;
import com.arqnova.modelo.dto.request.LoginRequest;
import com.arqnova.modelo.dto.request.RegisterRequest;
import com.arqnova.modelo.dto.response.AuthResponse;
import com.arqnova.modelo.dto.response.CurrentUserResponse;
import com.arqnova.modelo.excepciones.DuplicateResourceException;
import com.arqnova.modelo.excepciones.UnauthorizedActionException;
import com.arqnova.modelo.excepciones.AuthenticationRequiredException;
import com.arqnova.servicios.puertos.salida.UserStore;
import com.arqnova.servicios.AuthService;
import com.arqnova.servicios.puertos.salida.AuthTokenPort;
import com.arqnova.servicios.puertos.salida.PasswordPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;

@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserStore usuarioRepository;
    private final AuthTokenPort tokenService;
    private final PasswordPort passwordService;

    @Override
    public AuthResponse login(LoginRequest request) {
        log.info("Intento de login para usuario: {}", request.getUsername());

        String username = request.getUsername().trim().toLowerCase();
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationRequiredException("Correo o contraseña incorrectos"));

        if (Boolean.FALSE.equals(usuario.getHabilitado())) {
            throw new UnauthorizedActionException("Tu cuenta se encuentra deshabilitada");
        }
        if (!passwordService.matches(request.getPassword(), usuario.getPassword())) {
            throw new AuthenticationRequiredException("Correo o contraseña incorrectos");
        }
        // Migración transparente de cuentas sembradas antes de CU1.
        if (!passwordService.isEncoded(usuario.getPassword())) {
            usuario.setPassword(passwordService.encode(request.getPassword()));
            usuarioRepository.save(usuario);
        }

        return buildAuthResponse(usuario);
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        log.info("Intento de registro para usuario: {}", request.getUsername());

        String username = request.getUsername().trim().toLowerCase();
        if (usuarioRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Ya existe un usuario registrado con este correo");
        }

        Usuario nuevoUsuario = Usuario.builder()
                .username(username)
                .password(passwordService.encode(request.getPassword()))
                .nombreCompleto(request.getNombreCompleto())
                .rol(com.arqnova.modelo.enums.RolUsuario.SOLICITANTE)
                .departamento(request.getDepartamento() == null || request.getDepartamento().isBlank() ? "Sistemas" : request.getDepartamento().trim())
                .avatarUrl(generarAvatarUrl(request.getNombreCompleto()))
                .habilitado(true)
                .fechaCreacion(LocalDateTime.now())
                .build();

        usuarioRepository.save(nuevoUsuario);

        return buildAuthResponse(nuevoUsuario);
    }

    @Override
    public CurrentUserResponse currentUser(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedActionException("La sesión ya no es válida"));
        if (Boolean.FALSE.equals(usuario.getHabilitado())) {
            throw new UnauthorizedActionException("Tu cuenta se encuentra deshabilitada");
        }
        return CurrentUserResponse.builder()
                .username(usuario.getUsername())
                .nombreCompleto(usuario.getNombreCompleto())
                .rol(usuario.getRol())
                .departamento(usuario.getDepartamento())
                .avatarUrl(usuario.getAvatarUrl())
                .habilitado(!Boolean.FALSE.equals(usuario.getHabilitado()))
                .build();
    }

    private AuthResponse buildAuthResponse(Usuario usuario) {
        return AuthResponse.builder()
                .token(tokenService.emitir(usuario))
                .username(usuario.getUsername())
                .nombreCompleto(usuario.getNombreCompleto())
                .rol(usuario.getRol())
                .departamento(usuario.getDepartamento())
                .avatarUrl(usuario.getAvatarUrl())
                .habilitado(!Boolean.FALSE.equals(usuario.getHabilitado()))
                .build();
    }

    private String generarAvatarUrl(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            return "https://ui-avatars.com/api/?name=U&background=334155&color=fff&bold=true&size=128";
        }
        String encoded = nombreCompleto.trim().replace(" ", "+");
        return "https://ui-avatars.com/api/?name=" + encoded + "&background=random&color=fff&bold=true&size=128";
    }
}
