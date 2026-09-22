package com.arqnova.persistence.jpa;

import com.arqnova.domain.enums.RolUsuario;
import com.arqnova.persistence.jpa.entity.UserEntity;
import com.arqnova.persistence.jpa.repository.UserJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Transactional
class UserJpaRepositoryIntegrationTest {
    @Autowired UserJpaRepository users;
    @Test void persistsAndQueriesAllAuthenticationFields(){
        users.saveAndFlush(UserEntity.builder().id("it-user-id").username("it-user@test.local")
                .passwordHash("legacy-compatible-value").fullName("Integration User")
                .role(RolUsuario.SOLICITANTE).department("Sistemas").avatarUrl("/avatar.png").enabled(true).build());
        var loaded=users.findByUsernameIgnoreCase("IT-USER@TEST.LOCAL").orElseThrow();
        assertEquals("it-user-id",loaded.getId());
        assertEquals("legacy-compatible-value",loaded.getPasswordHash());
        assertTrue(loaded.isEnabled());
        assertTrue(users.existsByUsernameIgnoreCase("it-user@test.local"));
        assertEquals(1,users.findByEnabled(true).stream().filter(u->u.getId().equals("it-user-id")).count());
    }
}
