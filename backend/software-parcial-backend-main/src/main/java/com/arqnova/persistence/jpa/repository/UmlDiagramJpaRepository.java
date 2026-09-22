package com.arqnova.persistence.jpa.repository;

import com.arqnova.persistence.jpa.entity.UmlDiagramEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface UmlDiagramJpaRepository extends JpaRepository<UmlDiagramEntity, String> {
    Optional<UmlDiagramEntity> findByProjectId(String projectId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from UmlDiagramEntity d where d.projectId = :projectId")
    Optional<UmlDiagramEntity> findByProjectIdForUpdate(String projectId);
    long countByProjectId(String projectId);
    boolean existsByProjectId(String projectId);
}
