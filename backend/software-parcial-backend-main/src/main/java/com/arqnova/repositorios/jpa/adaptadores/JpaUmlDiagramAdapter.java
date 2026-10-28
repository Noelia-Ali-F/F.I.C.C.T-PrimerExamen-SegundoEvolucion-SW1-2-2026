package com.arqnova.repositorios.jpa.adaptadores;
import org.springframework.context.annotation.Profile;
import com.arqnova.modelo.entidades.*;
import com.arqnova.servicios.puertos.salida.UmlDiagramDataPort;
import com.arqnova.repositorios.jpa.UmlDiagramJpaRepository;
import java.util.*;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
@Profile("!dynamodb")
@Repository @RequiredArgsConstructor
public class JpaUmlDiagramAdapter implements UmlDiagramDataPort {
 private final UmlDiagramJpaRepository repository;
 public Optional<UmlDiagramEntity> findById(String id){return repository.findById(id).map(this::domain);}
 public boolean existsById(String id){return repository.existsById(id);}
 public UmlDiagramEntity save(UmlDiagramEntity value){return domain(repository.saveAndFlush(entity(value)));}
 public void deleteById(String id){repository.deleteById(id);}
 public List<UmlDiagramEntity> findAll(){return repository.findAll().stream().map(this::domain).toList();}
 public long count(){return repository.count();}
 public void deleteAll(){repository.deleteAll();}
 public void flush(){repository.flush();}
 public Optional<UmlDiagramEntity> findByProjectId(String projectId){return repository.findByProjectId(projectId).map(this::domain);}
 public Optional<UmlDiagramEntity> findByProjectIdForUpdate(String projectId){return repository.findByProjectIdForUpdate(projectId).map(this::domain);}
 public long countByProjectId(String projectId){return repository.countByProjectId(projectId);}
 public boolean existsByProjectId(String projectId){return repository.existsByProjectId(projectId);}
 private UmlDiagramEntity domain(com.arqnova.repositorios.jpa.entidades.UmlDiagramEntity value){return UmlDiagramEntity.builder().id(value.getId()).projectId(value.getProjectId()).model(value.getModel()).modelVersion(value.getModelVersion()).createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt()).build();}
 private com.arqnova.repositorios.jpa.entidades.UmlDiagramEntity entity(UmlDiagramEntity value){return com.arqnova.repositorios.jpa.entidades.UmlDiagramEntity.builder().id(value.getId()).projectId(value.getProjectId()).model(value.getModel()).modelVersion(value.getModelVersion()).createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt()).build();}
}
