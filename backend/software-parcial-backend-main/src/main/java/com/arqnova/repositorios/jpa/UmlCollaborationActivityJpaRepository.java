package com.arqnova.repositorios.jpa;

import com.arqnova.repositorios.jpa.entidades.UmlCollaborationActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UmlCollaborationActivityJpaRepository extends JpaRepository<UmlCollaborationActivityEntity, String> {
    List<UmlCollaborationActivityEntity> findTop100BySessionIdOrderByOccurredAtDesc(String sessionId);
    List<UmlCollaborationActivityEntity> findByProjectIdOrderByOccurredAtDescEventIdDesc(String projectId,org.springframework.data.domain.Pageable page);
    @org.springframework.data.jpa.repository.Query("select a from UmlCollaborationActivityEntity a where a.projectId=:project and (a.occurredAt<:before or (a.occurredAt=:before and a.eventId<:beforeId)) order by a.occurredAt desc,a.eventId desc")
    List<UmlCollaborationActivityEntity> historyBefore(@org.springframework.data.repository.query.Param("project")String project,
        @org.springframework.data.repository.query.Param("before")java.time.LocalDateTime before,
        @org.springframework.data.repository.query.Param("beforeId")String beforeId,org.springframework.data.domain.Pageable page);
}
