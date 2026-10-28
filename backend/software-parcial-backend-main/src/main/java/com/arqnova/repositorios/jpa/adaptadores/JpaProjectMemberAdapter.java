package com.arqnova.repositorios.jpa.adaptadores;
import com.arqnova.modelo.entidades.*;
import com.arqnova.servicios.puertos.salida.ProjectMemberDataPort;
import com.arqnova.repositorios.jpa.ProjectMemberJpaRepository;
import java.util.*;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
@Repository @RequiredArgsConstructor
public class JpaProjectMemberAdapter implements ProjectMemberDataPort {
 private final ProjectMemberJpaRepository repository;
 public Optional<ProjectMemberEntity> findById(ProjectMemberId id){return repository.findById(new com.arqnova.repositorios.jpa.entidades.ProjectMemberId(id.getProjectId(),id.getUserId())).map(this::domain);}
 public boolean existsById(ProjectMemberId id){return repository.existsById(new com.arqnova.repositorios.jpa.entidades.ProjectMemberId(id.getProjectId(),id.getUserId()));}
 public ProjectMemberEntity save(ProjectMemberEntity value){return domain(repository.saveAndFlush(entity(value)));}
 public void deleteById(ProjectMemberId id){repository.deleteById(new com.arqnova.repositorios.jpa.entidades.ProjectMemberId(id.getProjectId(),id.getUserId()));}
 public List<ProjectMemberEntity> findAll(){return repository.findAll().stream().map(this::domain).toList();}
 public long count(){return repository.count();}
 public void deleteAll(){repository.deleteAll();}
 public void flush(){repository.flush();}
 public long countByIdProjectId(String projectId){return repository.countByIdProjectId(projectId);}
 public Optional<ProjectMemberEntity> findByIdProjectIdAndIdUserId(String projectId,String userId){return repository.findByIdProjectIdAndIdUserId(projectId,userId).map(this::domain);}
 public List<ProjectMemberEntity> findByIdUserId(String userId){return repository.findByIdUserId(userId).stream().map(this::domain).toList();}
 private ProjectMemberEntity domain(com.arqnova.repositorios.jpa.entidades.ProjectMemberEntity value){return ProjectMemberEntity.builder().id(new com.arqnova.modelo.entidades.ProjectMemberId(value.getId().getProjectId(),value.getId().getUserId())).memberRole(value.getMemberRole()).createdAt(value.getCreatedAt()).build();}
 private com.arqnova.repositorios.jpa.entidades.ProjectMemberEntity entity(ProjectMemberEntity value){return com.arqnova.repositorios.jpa.entidades.ProjectMemberEntity.builder().id(new com.arqnova.repositorios.jpa.entidades.ProjectMemberId(value.getId().getProjectId(),value.getId().getUserId())).memberRole(value.getMemberRole()).createdAt(value.getCreatedAt()).build();}
}
