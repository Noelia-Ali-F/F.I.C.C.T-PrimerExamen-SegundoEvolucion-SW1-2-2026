package com.arqnova.persistence;

import com.arqnova.domain.enums.RolUsuario;
import com.arqnova.domain.model.Usuario;
import com.arqnova.persistence.jpa.entity.UserEntity;
import com.arqnova.persistence.jpa.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component @RequiredArgsConstructor
public class PostgresUserStore implements UserStore {
    private final UserJpaRepository repository;
    public Optional<Usuario> findById(String id){return repository.findById(id).map(this::domain);}
    public Optional<Usuario> findByUsername(String value){return repository.findByUsernameIgnoreCase(value).map(this::domain);}
    public boolean existsByUsername(String value){return repository.existsByUsernameIgnoreCase(value);}
    public List<Usuario> findAll(){return repository.findAll().stream().map(this::domain).toList();}
    public List<Usuario> findByRole(RolUsuario role){return repository.findByRole(role).stream().map(this::domain).toList();}
    public List<Usuario> findByRoleAndDepartment(RolUsuario role,String department){return repository.findByRoleAndDepartmentIgnoreCase(role,department).stream().map(this::domain).toList();}
    public Usuario save(Usuario u){
        if(u.getId()==null||u.getId().isBlank())u.setId(UUID.randomUUID().toString());
        var e=repository.findById(u.getId()).orElseGet(UserEntity::new);
        e.setId(u.getId());e.setUsername(u.getUsername());e.setPasswordHash(u.getPassword());e.setFullName(u.getNombreCompleto());e.setRole(u.getRol());e.setDepartment(u.getDepartamento());e.setAvatarUrl(u.getAvatarUrl());e.setEnabled(!Boolean.FALSE.equals(u.getHabilitado()));e.setCreatedAt(u.getFechaCreacion());
        return domain(repository.save(e));
    }
    public void delete(Usuario u){repository.deleteById(u.getId());}
    public long count(){return repository.count();}
    public void deleteAll(){repository.deleteAll();}
    private Usuario domain(UserEntity e){return Usuario.builder().id(e.getId()).username(e.getUsername()).password(e.getPasswordHash()).nombreCompleto(e.getFullName()).rol(e.getRole()).departamento(e.getDepartment()).avatarUrl(e.getAvatarUrl()).habilitado(e.isEnabled()).fechaCreacion(e.getCreatedAt()).build();}
}
