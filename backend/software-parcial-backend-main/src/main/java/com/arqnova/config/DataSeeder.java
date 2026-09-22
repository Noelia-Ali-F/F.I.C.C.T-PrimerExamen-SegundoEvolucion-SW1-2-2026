package com.arqnova.config;

import com.arqnova.domain.enums.RolUsuario;
import com.arqnova.domain.model.Departamento;
import com.arqnova.domain.model.Usuario;
import com.arqnova.persistence.UserStore;
import com.arqnova.repository.DepartamentoRepository;
import com.arqnova.security.PasswordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserStore userStore;
    private final DepartamentoRepository departamentoRepository;
    private final PasswordService passwordService;
    private final Environment environment;

    @Override
    public void run(String... args) {
        if (isForceSeedRequested() && isSeedProfileActive()) {
            log.warn("Perfil seed y FORCE_SEED habilitados. Ejecutando seed mínimo de ArqNova.");
            forceSeed();
            return;
        }
        if (isForceSeedRequested()) {
            log.warn("FORCE_SEED ignorado: el perfil 'seed' no está activo.");
        }
        log.info("Seed de ArqNova omitido. Se requieren perfil 'seed' y FORCE_SEED=true.");
    }

    public void forceSeed() {
        if (!isSeedProfileActive() || !isForceSeedRequested()) {
            throw new IllegalStateException(
                    "Seed bloqueado: se requieren el perfil activo 'seed' y FORCE_SEED=true"
            );
        }

        userStore.deleteAll();
        departamentoRepository.deleteAll();

        seedDepartamentos();
        seedUsuarios();
        log.info("Seed mínimo de ArqNova finalizado: usuarios, roles y departamentos.");
    }

    private void seedDepartamentos() {
        departamentoRepository.saveAll(List.of(
                departamento("Sistemas", "Tecnología y soporte de la plataforma ArqNova."),
                departamento("Modelado", "Diseño y revisión de modelos UML."),
                departamento("Administración", "Administración de usuarios y permisos.")
        ));
    }

    private void seedUsuarios() {
        userStore.saveAll(List.of(
                usuario("admin", "admin", "Súper Administrador", RolUsuario.ADMINISTRADOR, "Administración")
        ));
    }

    private Departamento departamento(String nombre, String descripcion) {
        return Departamento.builder()
                .nombre(nombre)
                .descripcion(descripcion)
                .creadoPor("seed")
                .activo(true)
                .fechaCreacion(LocalDateTime.now())
                .build();
    }

    private Usuario usuario(String username, String rawPassword, String nombre, RolUsuario rol, String departamento) {
        return Usuario.builder()
                .username(username)
                .password(passwordService.encode(rawPassword))
                .nombreCompleto(nombre)
                .rol(rol)
                .departamento(departamento)
                .habilitado(true)
                .fechaCreacion(LocalDateTime.now())
                .build();
    }

    private boolean isSeedProfileActive() {
        return environment.acceptsProfiles(Profiles.of("seed"));
    }

    private boolean isForceSeedRequested() {
        return Boolean.parseBoolean(System.getenv("FORCE_SEED"))
                || "true".equalsIgnoreCase(System.getProperty("force.seed"));
    }
}
