package com.arqnova.repositorios.jpa;

import com.arqnova.repositorios.jpa.entidades.UmlCollaborationSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UmlCollaborationSessionJpaRepository extends JpaRepository<UmlCollaborationSessionEntity, String> {
    Optional<UmlCollaborationSessionEntity> findFirstByProjectIdAndActiveTrue(String projectId);
    Optional<UmlCollaborationSessionEntity> findByInviteCodeAndActiveTrue(String inviteCode);
}
