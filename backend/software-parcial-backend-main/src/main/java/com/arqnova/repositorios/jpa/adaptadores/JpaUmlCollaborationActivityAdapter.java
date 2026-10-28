package com.arqnova.repositorios.jpa.adaptadores;
import com.arqnova.modelo.entidades.*;
import com.arqnova.servicios.puertos.salida.UmlCollaborationActivityDataPort;
import com.arqnova.repositorios.jpa.UmlCollaborationActivityJpaRepository;
import java.util.*;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
@Repository @RequiredArgsConstructor
public class JpaUmlCollaborationActivityAdapter implements UmlCollaborationActivityDataPort {
 private final UmlCollaborationActivityJpaRepository repository;
 public Optional<UmlCollaborationActivityEntity> findById(String id){return repository.findById(id).map(this::domain);}
 public boolean existsById(String id){return repository.existsById(id);}
 public UmlCollaborationActivityEntity save(UmlCollaborationActivityEntity value){return domain(repository.saveAndFlush(entity(value)));}
 public void deleteById(String id){repository.deleteById(id);}
 public List<UmlCollaborationActivityEntity> findAll(){return repository.findAll().stream().map(this::domain).toList();}
 public long count(){return repository.count();}
 public void deleteAll(){repository.deleteAll();}
 public void flush(){repository.flush();}
 public List<UmlCollaborationActivityEntity> findTop100BySessionIdOrderByOccurredAtDesc(String sessionId){return repository.findTop100BySessionIdOrderByOccurredAtDesc(sessionId).stream().map(this::domain).toList();}
 private UmlCollaborationActivityEntity domain(com.arqnova.repositorios.jpa.entidades.UmlCollaborationActivityEntity value){return UmlCollaborationActivityEntity.builder().eventId(value.getEventId()).sessionId(value.getSessionId()).projectId(value.getProjectId()).userId(value.getUserId()).userName(value.getUserName()).eventType(value.getEventType()).elementId(value.getElementId()).elementType(value.getElementType()).operation(value.getOperation()).eventData(value.getEventData()).occurredAt(value.getOccurredAt()).build();}
 private com.arqnova.repositorios.jpa.entidades.UmlCollaborationActivityEntity entity(UmlCollaborationActivityEntity value){return com.arqnova.repositorios.jpa.entidades.UmlCollaborationActivityEntity.builder().eventId(value.getEventId()).sessionId(value.getSessionId()).projectId(value.getProjectId()).userId(value.getUserId()).userName(value.getUserName()).eventType(value.getEventType()).elementId(value.getElementId()).elementType(value.getElementType()).operation(value.getOperation()).eventData(value.getEventData()).occurredAt(value.getOccurredAt()).build();}
}
