package com.arqnova.repositorios.jpa;

import com.arqnova.repositorios.jpa.entidades.DepartmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DepartmentJpaRepository extends JpaRepository<DepartmentEntity, String> {
    boolean existsByNameIgnoreCase(String name);
    Optional<DepartmentEntity> findByNameIgnoreCase(String name);
    List<DepartmentEntity> findAllByActiveTrueOrderByNameAsc();
    List<DepartmentEntity> findAllByOrderByNameAsc();
}
