package com.arqnova.persistence.jpa.repository;

import com.arqnova.persistence.jpa.entity.UmlCollaborationActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UmlCollaborationActivityJpaRepository extends JpaRepository<UmlCollaborationActivityEntity, String> {
    List<UmlCollaborationActivityEntity> findTop100BySessionIdOrderByOccurredAtDesc(String sessionId);
}
