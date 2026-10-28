package com.arqnova.repositorios.dynamodb;

import com.arqnova.modelo.entidades.UserEntity;
import com.arqnova.modelo.enums.RolUsuario;
import com.arqnova.servicios.puertos.salida.UserDataPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;

@Repository @Profile("dynamodb")
public class DynamoUserAdapter extends DynamoEntityAdapter<UserEntity, String> implements UserDataPort {
    public DynamoUserAdapter(DynamoDocumentStore store) { super(store, "USER", UserEntity.class, UserEntity::getId, v -> v); }
    protected void prepare(UserEntity v, UserEntity old) {
        Objects.requireNonNull(v.getUsername()); Objects.requireNonNull(v.getPasswordHash()); Objects.requireNonNull(v.getRole());
        if (old != null && !old.getUsername().equalsIgnoreCase(v.getUsername())) store.release("username", old.getUsername(), v.getId());
        store.reserve("username", v.getUsername(), v.getId());
        v.setCreatedAt(old != null ? old.getCreatedAt() : v.getCreatedAt() == null ? LocalDateTime.now() : v.getCreatedAt());
        if (old != null || v.getUpdatedAt() == null) v.setUpdatedAt(LocalDateTime.now());
    }
    protected void deleting(UserEntity v) { store.release("username", v.getUsername(), v.getId()); }
    public Optional<UserEntity> findByUsernameIgnoreCase(String name) { return store.get("UNIQUE", "username:" + name.toLowerCase(Locale.ROOT), DynamoDocumentStore.Unique.class).flatMap(v -> findById(v.owner())); }
    public boolean existsByUsernameIgnoreCase(String name) { return findByUsernameIgnoreCase(name).isPresent(); }
    public List<UserEntity> findByRole(RolUsuario role) { return findAll().stream().filter(v -> v.getRole() == role).toList(); }
    public List<UserEntity> findByRoleAndDepartmentIgnoreCase(RolUsuario role, String department) { return findByRole(role).stream().filter(v -> v.getDepartment() != null && v.getDepartment().equalsIgnoreCase(department)).toList(); }
    public List<UserEntity> findByEnabled(boolean enabled) { return findAll().stream().filter(v -> v.isEnabled() == enabled).toList(); }
}
