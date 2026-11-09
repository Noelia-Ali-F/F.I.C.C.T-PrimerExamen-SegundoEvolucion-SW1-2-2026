package com.arqnova.repositorios.jpa;

import com.arqnova.repositorios.jpa.entidades.UmlCollaborationActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UmlCollaborationActivityJpaRepository extends JpaRepository<UmlCollaborationActivityEntity, String> {
    List<UmlCollaborationActivityEntity> findTop100BySessionIdOrderByOccurredAtDesc(String sessionId);
}
