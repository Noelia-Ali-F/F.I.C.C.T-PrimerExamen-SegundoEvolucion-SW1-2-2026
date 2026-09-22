package com.arqnova.controller;

import com.arqnova.dto.request.UmlCollaborationEvent;
import com.arqnova.persistence.UserStore;
import com.arqnova.service.UmlCollaborationService;
import com.arqnova.service.UmlElementLockService;
import com.arqnova.exception.UnauthorizedActionException;
import com.arqnova.persistence.jpa.entity.ProjectMemberRole;
import com.arqnova.persistence.jpa.service.UmlProjectPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@RequiredArgsConstructor
public class UmlCollaborationWebSocketController {
    private final UmlCollaborationService service;
    private final UserStore usuarioRepository;
    private final SimpMessagingTemplate messaging;
    private final UmlElementLockService lockService;
    private final UmlProjectPersistenceService projects;
    private final Map<String, Map<String, Participant>> rooms = new ConcurrentHashMap<>();
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    @MessageMapping("/uml/{sessionId}")
    public void event(@DestinationVariable String sessionId, UmlCollaborationEvent event,
                      Principal principal, SimpMessageHeaderAccessor headers) {
        if (principal == null) throw new IllegalArgumentException("Autenticación requerida");
        var collaborationSession = service.requireSession(sessionId, event.getProjectId());
        String username = principal.getName();
        var user = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario inexistente"));
        if (Boolean.FALSE.equals(user.getHabilitado())) throw new IllegalArgumentException("Cuenta deshabilitada");

        event.setSessionId(sessionId);
        event.setUserId(username);
        event.setUserName(user.getNombreCompleto() == null || user.getNombreCompleto().isBlank() ? username : user.getNombreCompleto());
        event.setTimestamp(System.currentTimeMillis());
        if (event.getEventId() == null) event.setEventId(UUID.randomUUID().toString());

        if ("session:join".equals(event.getType())) {
            projects.requireAccess(event.getProjectId(), username);
            join(headers.getSessionId(), event, username.equals(collaborationSession.getCreatedBy()));
            return;
        }
        if ("session:leave".equals(event.getType())) {
            leave(headers.getSessionId());
            return;
        }
        if ("lock:acquire".equals(event.getType())) {
            projects.requireAccess(event.getProjectId(), username, ProjectMemberRole.OWNER, ProjectMemberRole.EDITOR);
            acquireLock(headers.getSessionId(), event);
            return;
        }
        if ("lock:renew".equals(event.getType())) {
            renewLock(headers.getSessionId(), event);
            return;
        }
        if ("lock:release".equals(event.getType())) {
            releaseLock(headers.getSessionId(), event);
            return;
        }
        try {
            projects.requireAccess(event.getProjectId(), username, ProjectMemberRole.OWNER, ProjectMemberRole.EDITOR);
            if (requiresLock(event.getType())) {
                String requiredType = requiredElementType(event.getType());
                if (!requiredType.equals(event.getElementType()))
                    throw new UnauthorizedActionException("La operación requiere un bloqueo de tipo " + requiredType);
                lockService.requireOwnerAndRenew(sessionId, event.getElementId(), event.getElementType(),
                        event.getUserId(), headers.getSessionId());
            }
            service.apply(event);
            messaging.convertAndSend(topic(sessionId), event);
        } catch (UnauthorizedActionException exception) {
            var owner = lockService.find(sessionId, event.getElementType(), event.getElementId()).orElse(null);
            messaging.convertAndSend(topic(sessionId), lockEvent("lock:denied", event, owner,
                    exception.getMessage(), event.getUserId()));
        }
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) { leave(event.getSessionId()); }

    private void join(String socketId, UmlCollaborationEvent event, boolean host) {
        connections.put(socketId, new Connection(event.getSessionId(), event.getProjectId(), event.getUserId(), event.getUserName()));
        rooms.computeIfAbsent(event.getSessionId(), key -> new ConcurrentHashMap<>())
                .put(socketId, new Participant(event.getUserId(), event.getUserName(), "online", host));
        service.recordActivity(broadcastParticipants(event.getSessionId(), event.getProjectId(), event.getUserId(), event.getUserName(), "session:join"));
    }

    private void leave(String socketId) {
        lockService.releaseBySocket(socketId).forEach(lock -> messaging.convertAndSend(topic(lock.sessionId()),
                lockEvent("lock:released", lock, "Bloqueo liberado al salir de la sesión", null)));
        Connection connection = connections.remove(socketId);
        if (connection == null) return;
        Map<String, Participant> room = rooms.get(connection.sessionId());
        if (room != null) {
            room.remove(socketId);
            service.recordActivity(broadcastParticipants(connection.sessionId(), connection.projectId(), connection.userId(), connection.userName(), "session:leave"));
            if (room.isEmpty()) rooms.remove(connection.sessionId());
        }
    }

    private UmlCollaborationEvent broadcastParticipants(String sessionId, String projectId, String userId, String userName, String type) {
        Map<String, Participant> room = rooms.getOrDefault(sessionId, Map.of());
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Participant> connectedUsers = new LinkedHashMap<>();
        room.values().forEach(participant -> connectedUsers.put(participant.userId(), participant));
        data.put("participants", new ArrayList<>(connectedUsers.values()));
        UmlCollaborationEvent presence = UmlCollaborationEvent.builder().eventId(UUID.randomUUID().toString())
                .type(type).sessionId(sessionId).projectId(projectId).userId(userId).userName(userName)
                .operation(type.endsWith("join") ? "se unió a la sesión" : "salió de la sesión")
                .data(data).timestamp(System.currentTimeMillis()).build();
        messaging.convertAndSend(topic(sessionId), presence);
        return presence;
    }

    private String topic(String sessionId) { return "/topic/uml/" + sessionId; }

    private void acquireLock(String socketId, UmlCollaborationEvent event) {
        var result = lockService.acquire(event.getProjectId(), event.getSessionId(), event.getElementId(),
                event.getElementType(), event.getUserId(), event.getUserName(), socketId);
        String type = result.granted() ? "lock:granted" : "lock:denied";
        String operation = result.granted() ? "comenzó a editar" : "Elemento ocupado";
        messaging.convertAndSend(topic(event.getSessionId()), lockEvent(type, event, result.lock(), operation, event.getUserId()));
    }

    private void renewLock(String socketId, UmlCollaborationEvent event) {
        try {
            var lock = lockService.requireOwnerAndRenew(event.getSessionId(), event.getElementId(),
                    event.getElementType(), event.getUserId(), socketId);
            messaging.convertAndSend(topic(event.getSessionId()),
                    lockEvent("lock:granted", event, lock, "continúa editando", event.getUserId()));
        } catch (UnauthorizedActionException exception) {
            var owner = lockService.find(event.getSessionId(), event.getElementType(), event.getElementId()).orElse(null);
            messaging.convertAndSend(topic(event.getSessionId()),
                    lockEvent("lock:denied", event, owner, exception.getMessage(), event.getUserId()));
        }
    }

    private void releaseLock(String socketId, UmlCollaborationEvent event) {
        lockService.release(event.getSessionId(), event.getElementId(), event.getElementType(), event.getUserId(), socketId)
                .ifPresent(lock -> messaging.convertAndSend(topic(event.getSessionId()),
                        lockEvent("lock:released", lock, "finalizó la edición", null)));
    }

    @Scheduled(fixedDelayString = "${uml.collaboration.lock-cleanup-ms:5000}")
    public void expireLocks() {
        lockService.expireNow().forEach(lock -> messaging.convertAndSend(topic(lock.sessionId()),
                lockEvent("lock:expired", lock, "El bloqueo venció", null)));
    }

    private boolean requiresLock(String type) {
        return "class:update".equals(type) || "class:move".equals(type) || "class:delete".equals(type)
                || "attribute:create".equals(type) || "attribute:update".equals(type) || "attribute:delete".equals(type)
                || "relation:update".equals(type) || "relation:delete".equals(type);
    }

    private String requiredElementType(String type) {
        if (type.startsWith("attribute:")) return "attribute";
        if (type.startsWith("relation:")) return "relation";
        return "class";
    }

    private UmlCollaborationEvent lockEvent(String type, UmlCollaborationEvent request,
                                            UmlElementLockService.ElementLock lock, String operation,
                                            String requesterId) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (lock != null) data.put("lock", lock);
        if (requesterId != null) data.put("requesterId", requesterId);
        return UmlCollaborationEvent.builder().eventId(UUID.randomUUID().toString()).type(type)
                .projectId(request.getProjectId()).sessionId(request.getSessionId()).elementId(request.getElementId())
                .elementType(request.getElementType()).userId(lock == null ? request.getUserId() : lock.userId())
                .userName(lock == null ? request.getUserName() : lock.userName()).operation(operation)
                .data(data).timestamp(System.currentTimeMillis()).build();
    }

    private UmlCollaborationEvent lockEvent(String type, UmlElementLockService.ElementLock lock,
                                            String operation, String requesterId) {
        UmlCollaborationEvent request = UmlCollaborationEvent.builder().projectId(lock.projectId())
                .sessionId(lock.sessionId()).elementId(lock.elementId()).elementType(lock.elementType())
                .userId(lock.userId()).userName(lock.userName()).build();
        return lockEvent(type, request, lock, operation, requesterId);
    }
    public record Participant(String userId, String userName, String status, boolean host) {}
    private record Connection(String sessionId, String projectId, String userId, String userName) {}
}
