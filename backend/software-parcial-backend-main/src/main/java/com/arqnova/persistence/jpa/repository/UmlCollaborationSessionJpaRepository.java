package com.arqnova.persistence.jpa.repository;

import com.arqnova.persistence.jpa.entity.UmlCollaborationSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UmlCollaborationSessionJpaRepository extends JpaRepository<UmlCollaborationSessionEntity, String> {
    Optional<UmlCollaborationSessionEntity> findFirstByProjectIdAndActiveTrue(String projectId);
    Optional<UmlCollaborationSessionEntity> findByInviteCodeAndActiveTrue(String inviteCode);
}
