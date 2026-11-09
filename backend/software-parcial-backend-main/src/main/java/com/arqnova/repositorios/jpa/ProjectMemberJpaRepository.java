package com.arqnova.repositorios.jpa;

import com.arqnova.repositorios.jpa.entidades.ProjectMemberEntity;
import com.arqnova.repositorios.jpa.entidades.ProjectMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberJpaRepository extends JpaRepository<ProjectMemberEntity, ProjectMemberId> {
    @org.springframework.data.jpa.repository.Query(value="SELECT id FROM projects WHERE id = :projectId FOR UPDATE",nativeQuery=true)
    Optional<String> lockProject(String projectId);

    @org.springframework.data.jpa.repository.Query(value="SELECT member_role FROM project_members WHERE project_id = :projectId AND user_id = :userId FOR SHARE",nativeQuery=true)
    Optional<String> lockRole(String projectId,String userId);

    long countByIdProjectId(String projectId);
    Optional<ProjectMemberEntity> findByIdProjectIdAndIdUserId(String projectId, String userId);
    List<ProjectMemberEntity> findByIdUserId(String userId);
}
