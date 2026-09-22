package com.arqnova.security;

import com.arqnova.domain.enums.RolUsuario;
import com.arqnova.domain.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class AuthSecurityTest {

    @Test
    void passwordIsHashedAndCanBeVerified() {
        PasswordService service = new PasswordService();
        String hash = service.encode("Test1234");
        assertNotEquals("Test1234", hash);
        assertTrue(service.matches("Test1234", hash));
        assertFalse(service.matches("Otro1234", hash));
    }

    @Test
    void expiredTokenIsRejected() {
        AuthTokenService service = new AuthTokenService(new MockEnvironment());
        ReflectionTestUtils.setField(service, "secret", "test-secret-with-enough-entropy");
        ReflectionTestUtils.setField(service, "tokenTtlSeconds", -1L);
        Usuario user = Usuario.builder().username("juan@test.com").rol(RolUsuario.SOLICITANTE).departamento("Sistemas").build();
        String token = service.emitir(user);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.verificar(token));
        assertEquals("Token vencido", error.getMessage());
    }

    @Test
    void developmentCanUseLocalFallbackWhenSecretIsMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        AuthTokenService service = new AuthTokenService(environment);
        ReflectionTestUtils.setField(service, "secret", "");

        assertDoesNotThrow(service::validateConfiguration);
        assertNotNull(ReflectionTestUtils.getField(service, "secret"));
    }

    @Test
    void productionRefusesToStartWithoutValidSecret() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        AuthTokenService service = new AuthTokenService(environment);
        ReflectionTestUtils.setField(service, "secret", "");

        IllegalStateException error = assertThrows(IllegalStateException.class, service::validateConfiguration);
        assertTrue(error.getMessage().contains("AUTH_TOKEN_SECRET"));
        assertFalse(error.getMessage().contains("workflow-development"));
    }

    @Test
    void productionRefusesSecretBelowMinimumLength() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        AuthTokenService service = new AuthTokenService(environment);
        ReflectionTestUtils.setField(service, "secret", "short-secret");

        assertThrows(IllegalStateException.class, service::validateConfiguration);
    }
}
