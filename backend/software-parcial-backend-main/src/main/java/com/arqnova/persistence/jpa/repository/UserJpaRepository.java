package com.arqnova.persistence.jpa.repository;

import com.arqnova.domain.enums.RolUsuario;
import com.arqnova.persistence.jpa.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface UserJpaRepository extends JpaRepository<UserEntity,String> {
    Optional<UserEntity> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    List<UserEntity> findByRole(RolUsuario role);
    List<UserEntity> findByRoleAndDepartmentIgnoreCase(RolUsuario role,String department);
    List<UserEntity> findByEnabled(boolean enabled);
}
