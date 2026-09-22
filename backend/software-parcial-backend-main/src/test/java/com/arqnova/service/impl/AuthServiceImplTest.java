package com.arqnova.service.impl;

import com.arqnova.domain.enums.RolUsuario;
import com.arqnova.domain.model.Usuario;
import com.arqnova.dto.response.CurrentUserResponse;
import com.arqnova.dto.request.LoginRequest;
import com.arqnova.dto.request.RegisterRequest;
import com.arqnova.exception.AuthenticationRequiredException;
import com.arqnova.exception.UnauthorizedActionException;
import com.arqnova.persistence.UserStore;
import com.arqnova.security.AuthTokenService;
import com.arqnova.security.PasswordService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    @Test
    void loginCorrectoValidaCredencialesYEmiteSesion() {
        UserStore repository = mock(UserStore.class);
        AuthTokenService tokenService = mock(AuthTokenService.class);
        PasswordService passwordService = mock(PasswordService.class);
        Usuario usuario = usuarioActivo();
        when(repository.findByUsername("juan@test.com")).thenReturn(Optional.of(usuario));
        when(passwordService.matches("Test1234", usuario.getPassword())).thenReturn(true);
        when(passwordService.isEncoded(usuario.getPassword())).thenReturn(true);
        when(tokenService.emitir(usuario)).thenReturn("token-seguro");

        var response = new AuthServiceImpl(repository, tokenService, passwordService)
                .login(LoginRequest.builder().username(" JUAN@TEST.COM ").password("Test1234").build());

        assertEquals("token-seguro", response.getToken());
        assertEquals("juan@test.com", response.getUsername());
        verify(tokenService).emitir(usuario);
    }

    @Test
    void loginIncorrectoNoRevelaSiElUsuarioExiste() {
        UserStore repository = mock(UserStore.class);
        AuthTokenService tokenService = mock(AuthTokenService.class);
        PasswordService passwordService = mock(PasswordService.class);
        Usuario usuario = usuarioActivo();
        when(repository.findByUsername("juan@test.com")).thenReturn(Optional.of(usuario));
        when(passwordService.matches("incorrecta", usuario.getPassword())).thenReturn(false);

        var error = assertThrows(AuthenticationRequiredException.class, () ->
                new AuthServiceImpl(repository, tokenService, passwordService)
                        .login(LoginRequest.builder().username("juan@test.com").password("incorrecta").build()));

        assertEquals("Correo o contraseña incorrectos", error.getMessage());
        verify(tokenService, never()).emitir(usuario);
    }

    @Test
    void loginRechazaUsuarioDeshabilitado() {
        UserStore repository = mock(UserStore.class);
        AuthTokenService tokenService = mock(AuthTokenService.class);
        PasswordService passwordService = mock(PasswordService.class);
        Usuario usuario = usuarioActivo();
        usuario.setHabilitado(false);
        when(repository.findByUsername("juan@test.com")).thenReturn(Optional.of(usuario));

        assertThrows(UnauthorizedActionException.class, () ->
                new AuthServiceImpl(repository, tokenService, passwordService)
                        .login(LoginRequest.builder().username("juan@test.com").password("Test1234").build()));
        verify(tokenService, never()).emitir(usuario);
    }

    @Test
    void registroCorrectoNormalizaCorreoYProtegePassword() {
        UserStore repository = mock(UserStore.class);
        AuthTokenService tokenService = mock(AuthTokenService.class);
        PasswordService passwordService = mock(PasswordService.class);
        when(passwordService.encode("Test1234")).thenReturn("hash");
        when(tokenService.emitir(org.mockito.ArgumentMatchers.any())).thenReturn("token");

        var response = new AuthServiceImpl(repository, tokenService, passwordService).register(
                RegisterRequest.builder().username(" NUEVO@TEST.COM ").password("Test1234")
                        .nombreCompleto("Nuevo Usuario").build());

        var captor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(captor.capture());
        assertEquals("nuevo@test.com", captor.getValue().getUsername());
        assertEquals("hash", captor.getValue().getPassword());
        assertTrue(captor.getValue().getHabilitado());
        assertNotNull(response.getToken());
    }

    @Test
    void currentUserReturnsProfileWithoutIssuingToken() {
        UserStore repository = mock(UserStore.class);
        AuthTokenService tokenService = mock(AuthTokenService.class);
        PasswordService passwordService = mock(PasswordService.class);
        Usuario usuario = Usuario.builder()
                .username("juan@test.com")
                .nombreCompleto("Juan Pérez")
                .rol(RolUsuario.SOLICITANTE)
                .departamento("Sistemas")
                .habilitado(true)
                .build();
        when(repository.findByUsername("juan@test.com")).thenReturn(Optional.of(usuario));
        AuthServiceImpl service = new AuthServiceImpl(repository, tokenService, passwordService);

        CurrentUserResponse response = service.currentUser("juan@test.com");

        assertEquals("juan@test.com", response.getUsername());
        assertTrue(response.isHabilitado());
        verify(tokenService, never()).emitir(usuario);
    }

    private Usuario usuarioActivo() {
        return Usuario.builder()
                .username("juan@test.com")
                .password("hash")
                .nombreCompleto("Juan Pérez")
                .rol(RolUsuario.SOLICITANTE)
                .departamento("Sistemas")
                .habilitado(true)
                .build();
    }
}
