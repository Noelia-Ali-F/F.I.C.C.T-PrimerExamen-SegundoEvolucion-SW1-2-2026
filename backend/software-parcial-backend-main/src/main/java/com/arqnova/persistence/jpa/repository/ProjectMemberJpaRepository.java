package com.arqnova.persistence.jpa.repository;

import com.arqnova.persistence.jpa.entity.ProjectMemberEntity;
import com.arqnova.persistence.jpa.entity.ProjectMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberJpaRepository extends JpaRepository<ProjectMemberEntity, ProjectMemberId> {
    long countByIdProjectId(String projectId);
    Optional<ProjectMemberEntity> findByIdProjectIdAndIdUserId(String projectId, String userId);
    List<ProjectMemberEntity> findByIdUserId(String userId);
}
