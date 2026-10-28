package com.arqnova.repositorios.dynamodb;

import com.arqnova.modelo.Usuario;
import com.arqnova.modelo.entidades.UserEntity;
import com.arqnova.modelo.enums.RolUsuario;
import com.arqnova.servicios.puertos.salida.UserStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository @Profile("dynamodb")
public class DynamoUserStore implements UserStore {
    private final DynamoUserAdapter users;
    public DynamoUserStore(DynamoUserAdapter users) { this.users = users; }
    public Optional<Usuario> findById(String id) { return users.findById(id).map(this::domain); }
    public Optional<Usuario> findByUsername(String name) { return users.findByUsernameIgnoreCase(name).map(this::domain); }
    public boolean existsByUsername(String name) { return users.existsByUsernameIgnoreCase(name); }
    public List<Usuario> findAll() { return users.findAll().stream().map(this::domain).toList(); }
    public List<Usuario> findByRole(RolUsuario role) { return users.findByRole(role).stream().map(this::domain).toList(); }
    public List<Usuario> findByRoleAndDepartment(RolUsuario role, String department) { return users.findByRoleAndDepartmentIgnoreCase(role, department).stream().map(this::domain).toList(); }
    public Usuario save(Usuario v) {
        if (v.getId() == null || v.getId().isBlank()) v.setId(UUID.randomUUID().toString());
        return domain(users.save(UserEntity.builder().id(v.getId()).username(v.getUsername()).passwordHash(v.getPassword())
                .fullName(v.getNombreCompleto()).role(v.getRol()).department(v.getDepartamento()).avatarUrl(v.getAvatarUrl())
                .enabled(!Boolean.FALSE.equals(v.getHabilitado())).createdAt(v.getFechaCreacion()).build()));
    }
    public void delete(Usuario v) { users.deleteById(v.getId()); }
    public long count() { return users.count(); }
    public void deleteAll() { users.deleteAll(); }
    private Usuario domain(UserEntity v) { return Usuario.builder().id(v.getId()).username(v.getUsername()).password(v.getPasswordHash())
            .nombreCompleto(v.getFullName()).rol(v.getRole()).departamento(v.getDepartment()).avatarUrl(v.getAvatarUrl())
            .habilitado(v.isEnabled()).fechaCreacion(v.getCreatedAt()).build(); }
}
