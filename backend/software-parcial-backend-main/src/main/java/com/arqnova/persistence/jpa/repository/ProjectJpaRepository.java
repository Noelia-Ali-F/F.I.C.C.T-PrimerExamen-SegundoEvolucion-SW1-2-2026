package com.arqnova.persistence.jpa.repository;

import com.arqnova.persistence.jpa.entity.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProjectJpaRepository extends JpaRepository<ProjectEntity, String> {
    List<ProjectEntity> findAllByOrderByUpdatedAtDesc();

    List<ProjectEntity> findByIdInOrderByUpdatedAtDesc(Collection<String> ids);

    boolean existsByOwnerUserIdAndNameIgnoreCase(String ownerUserId, String name);

    boolean existsByOwnerUserIdAndNameIgnoreCaseAndIdNot(String ownerUserId, String name, String id);
}
