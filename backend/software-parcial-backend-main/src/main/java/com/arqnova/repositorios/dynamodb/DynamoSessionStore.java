package com.arqnova.repositorios.dynamodb;

import com.arqnova.modelo.UmlCollaborationSession;
import com.arqnova.servicios.puertos.salida.CollaborationSessionStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;

@Repository @Profile("dynamodb")
public class DynamoSessionStore implements CollaborationSessionStore {
    private final DynamoDocumentStore store;
    public DynamoSessionStore(DynamoDocumentStore store) { this.store = store; }
    public Optional<UmlCollaborationSession> findById(String id) { return store.get("SESSION", id, UmlCollaborationSession.class); }
    public Optional<UmlCollaborationSession> findActiveByProjectId(String id) { return store.list("SESSION", UmlCollaborationSession.class).stream().filter(v -> v.isActive() && v.getProjectId().equals(id)).findFirst(); }
    public Optional<UmlCollaborationSession> findActiveByInviteCode(String code) {
        return store.get("UNIQUE", DynamoDocumentStore.uniqueKey("invite", code), DynamoDocumentStore.Unique.class).flatMap(v -> findById(v.owner())).filter(UmlCollaborationSession::isActive);
    }
    public UmlCollaborationSession save(UmlCollaborationSession v) {
        return store.atomic(() -> {
            store.requireParent("PROJECT", v.getProjectId());
            if (!Set.of("EDITOR", "VIEWER").contains(v.getInviteRole())) throw new IllegalArgumentException("Rol de invitación inválido");
            var old = findById(v.getId());
            old.filter(o -> !o.getInviteCode().equalsIgnoreCase(v.getInviteCode())).ifPresent(o -> store.release("invite", o.getInviteCode(), v.getId()));
            store.reserve("invite", v.getInviteCode(), v.getId());
            if (v.getCreatedAt() == null) v.setCreatedAt(old.map(UmlCollaborationSession::getCreatedAt).orElseGet(LocalDateTime::now));
            if (v.getUpdatedAt() == null) v.setUpdatedAt(LocalDateTime.now());
            return store.put("SESSION", v.getId(), v);
        });
    }
}
