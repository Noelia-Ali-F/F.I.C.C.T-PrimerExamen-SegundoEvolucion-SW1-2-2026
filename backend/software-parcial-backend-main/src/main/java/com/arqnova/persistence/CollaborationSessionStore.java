package com.arqnova.persistence;

import com.arqnova.domain.model.UmlCollaborationSession;
import java.util.Optional;

public interface CollaborationSessionStore {
    Optional<UmlCollaborationSession> findById(String id);
    Optional<UmlCollaborationSession> findActiveByProjectId(String projectId);
    Optional<UmlCollaborationSession> findActiveByInviteCode(String inviteCode);
    UmlCollaborationSession save(UmlCollaborationSession session);
}
