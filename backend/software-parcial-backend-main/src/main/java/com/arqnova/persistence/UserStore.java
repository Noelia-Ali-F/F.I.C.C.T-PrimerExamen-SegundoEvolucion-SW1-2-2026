package com.arqnova.persistence;

import com.arqnova.domain.enums.RolUsuario;
import com.arqnova.domain.model.Usuario;
import java.util.*;

public interface UserStore {
    Optional<Usuario> findById(String id);
    Optional<Usuario> findByUsername(String username);
    boolean existsByUsername(String username);
    List<Usuario> findAll();
    List<Usuario> findByRole(RolUsuario role);
    List<Usuario> findByRoleAndDepartment(RolUsuario role,String department);
    Usuario save(Usuario user);
    void delete(Usuario user);
    long count();
    void deleteAll();
    default List<Usuario> saveAll(Iterable<Usuario> users) { List<Usuario> result=new ArrayList<>(); users.forEach(user -> result.add(save(user))); return result; }
}
