package com.arqnova.repositorios.jpa.adaptadores;
import org.springframework.context.annotation.Profile;
import com.arqnova.modelo.entidades.*;
import com.arqnova.servicios.puertos.salida.ProjectDataPort;
import com.arqnova.repositorios.jpa.ProjectJpaRepository;
import java.util.*;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
@Profile("!dynamodb")
@Repository @RequiredArgsConstructor
public class JpaProjectAdapter implements ProjectDataPort {
 private final ProjectJpaRepository repository;
 public Optional<ProjectEntity> findById(String id){return repository.findById(id).map(this::domain);}
 public boolean existsById(String id){return repository.existsById(id);}
 public ProjectEntity save(ProjectEntity value){return domain(repository.saveAndFlush(entity(value)));}
 public void deleteById(String id){repository.deleteById(id);}
 public List<ProjectEntity> findAll(){return repository.findAll().stream().map(this::domain).toList();}
 public long count(){return repository.count();}
 public void deleteAll(){repository.deleteAll();}
 public void flush(){repository.flush();}
 public List<ProjectEntity> findAllByOrderByUpdatedAtDesc(){return repository.findAllByOrderByUpdatedAtDesc().stream().map(this::domain).toList();}
 public List<ProjectEntity> findByIdInOrderByUpdatedAtDesc(Collection<String> ids){return repository.findByIdInOrderByUpdatedAtDesc(ids).stream().map(this::domain).toList();}
 public boolean existsByOwnerUserIdAndNameIgnoreCase(String ownerUserId,String name){return repository.existsByOwnerUserIdAndNameIgnoreCase(ownerUserId,name);}
 public boolean existsByOwnerUserIdAndNameIgnoreCaseAndIdNot(String ownerUserId,String name,String id){return repository.existsByOwnerUserIdAndNameIgnoreCaseAndIdNot(ownerUserId,name,id);}
 private ProjectEntity domain(com.arqnova.repositorios.jpa.entidades.ProjectEntity value){return ProjectEntity.builder().id(value.getId()).name(value.getName()).description(value.getDescription()).status(value.getStatus()).ownerUserId(value.getOwnerUserId()).createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt()).build();}
 private com.arqnova.repositorios.jpa.entidades.ProjectEntity entity(ProjectEntity value){return com.arqnova.repositorios.jpa.entidades.ProjectEntity.builder().id(value.getId()).name(value.getName()).description(value.getDescription()).status(value.getStatus()).ownerUserId(value.getOwnerUserId()).createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt()).build();}
}
