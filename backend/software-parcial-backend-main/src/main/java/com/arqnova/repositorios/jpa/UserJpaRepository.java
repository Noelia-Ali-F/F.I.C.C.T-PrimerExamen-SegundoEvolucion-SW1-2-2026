package com.arqnova.repositorios.jpa;

import com.arqnova.modelo.enums.RolUsuario;
import com.arqnova.modelo.entidades.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface UserJpaRepository extends JpaRepository<UserEntity,String> {
    Optional<UserEntity> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    List<UserEntity> findByRole(RolUsuario role);
    List<UserEntity> findByRoleAndDepartmentIgnoreCase(RolUsuario role,String department);
    List<UserEntity> findByEnabled(boolean enabled);
}
