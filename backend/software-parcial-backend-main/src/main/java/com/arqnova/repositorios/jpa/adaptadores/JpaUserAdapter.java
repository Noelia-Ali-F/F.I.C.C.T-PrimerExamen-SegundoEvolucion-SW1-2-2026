package com.arqnova.repositorios.jpa.adaptadores;
import org.springframework.context.annotation.Profile;
import com.arqnova.modelo.entidades.*;
import com.arqnova.servicios.puertos.salida.UserDataPort;
import com.arqnova.repositorios.jpa.UserJpaRepository;
import java.util.*;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
@Profile("!dynamodb")
@Repository @RequiredArgsConstructor
public class JpaUserAdapter implements UserDataPort {
 private final UserJpaRepository repository;
 public Optional<UserEntity> findById(String id){return repository.findById(id).map(this::domain);}
 public boolean existsById(String id){return repository.existsById(id);}
 public UserEntity save(UserEntity value){return domain(repository.saveAndFlush(entity(value)));}
 public void deleteById(String id){repository.deleteById(id);}
 public List<UserEntity> findAll(){return repository.findAll().stream().map(this::domain).toList();}
 public long count(){return repository.count();}
 public void deleteAll(){repository.deleteAll();}
 public void flush(){repository.flush();}
 public Optional<UserEntity> findByUsernameIgnoreCase(String username){return repository.findByUsernameIgnoreCase(username).map(this::domain);}
 public boolean existsByUsernameIgnoreCase(String username){return repository.existsByUsernameIgnoreCase(username);}
 public List<UserEntity> findByRole(com.arqnova.modelo.enums.RolUsuario role){return repository.findByRole(role).stream().map(this::domain).toList();}
 public List<UserEntity> findByRoleAndDepartmentIgnoreCase(com.arqnova.modelo.enums.RolUsuario role,String department){return repository.findByRoleAndDepartmentIgnoreCase(role,department).stream().map(this::domain).toList();}
 public List<UserEntity> findByEnabled(boolean enabled){return repository.findByEnabled(enabled).stream().map(this::domain).toList();}
 private UserEntity domain(com.arqnova.repositorios.jpa.entidades.UserEntity value){return UserEntity.builder().id(value.getId()).username(value.getUsername()).passwordHash(value.getPasswordHash()).fullName(value.getFullName()).role(value.getRole()).department(value.getDepartment()).avatarUrl(value.getAvatarUrl()).enabled(value.isEnabled()).createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt()).build();}
 private com.arqnova.repositorios.jpa.entidades.UserEntity entity(UserEntity value){return com.arqnova.repositorios.jpa.entidades.UserEntity.builder().id(value.getId()).username(value.getUsername()).passwordHash(value.getPasswordHash()).fullName(value.getFullName()).role(value.getRole()).department(value.getDepartment()).avatarUrl(value.getAvatarUrl()).enabled(value.isEnabled()).createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt()).build();}
}
