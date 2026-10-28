package com.arqnova.servicios.puertos.salida;
import com.arqnova.modelo.entidades.*;
import java.util.*;
public interface UserDataPort extends EntityDataPort<UserEntity,String> {
 Optional<UserEntity> findByUsernameIgnoreCase(String username);
 boolean existsByUsernameIgnoreCase(String username);
 List<UserEntity> findByRole(com.arqnova.modelo.enums.RolUsuario role);
 List<UserEntity> findByRoleAndDepartmentIgnoreCase(com.arqnova.modelo.enums.RolUsuario role,String department);
 List<UserEntity> findByEnabled(boolean enabled);
}
