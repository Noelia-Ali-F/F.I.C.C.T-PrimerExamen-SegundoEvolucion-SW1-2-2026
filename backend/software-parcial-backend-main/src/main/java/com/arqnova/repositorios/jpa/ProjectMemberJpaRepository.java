package com.arqnova.repositorios.jpa;

import com.arqnova.repositorios.jpa.entidades.ProjectMemberEntity;
import com.arqnova.repositorios.jpa.entidades.ProjectMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberJpaRepository extends JpaRepository<ProjectMemberEntity, ProjectMemberId> {
    long countByIdProjectId(String projectId);
    Optional<ProjectMemberEntity> findByIdProjectIdAndIdUserId(String projectId, String userId);
    List<ProjectMemberEntity> findByIdUserId(String userId);
}
