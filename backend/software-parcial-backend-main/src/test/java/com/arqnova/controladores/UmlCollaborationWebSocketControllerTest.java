package com.arqnova.controladores;

import com.arqnova.modelo.UmlCollaborationSession;
import com.arqnova.modelo.Usuario;
import com.arqnova.modelo.dto.request.UmlCollaborationEvent;
import com.arqnova.repositorios.UserStore;
import com.arqnova.servicios.UmlCollaborationService;
import com.arqnova.servicios.UmlElementLockService;
import com.arqnova.servicios.persistencia.UmlProjectPersistenceService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UmlCollaborationWebSocketControllerTest {

    @Test
    void identifiesHostUsesRealNamesAndRemovesDisconnectedParticipant() {
        UmlCollaborationService service = mock(UmlCollaborationService.class);
        UserStore users = mock(UserStore.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        UmlElementLockService locks = mock(UmlElementLockService.class);
        UmlProjectPersistenceService projects = mock(UmlProjectPersistenceService.class);
        UmlCollaborationWebSocketController controller = new UmlCollaborationWebSocketController(
                service, new com.arqnova.servicios.UsuarioConsultaService(users), messaging, locks, projects);
        when(service.requireSession(eq("s1"), eq("p1"))).thenReturn(UmlCollaborationSession.builder()
                .id("s1").projectId("p1").createdBy("admin").active(true).build());
        when(users.findByUsername("admin")).thenReturn(Optional.of(Usuario.builder()
                .username("admin").nombreCompleto("Andrés Pérez").habilitado(true).build()));
        when(users.findByUsername("maria")).thenReturn(Optional.of(Usuario.builder()
                .username("maria").nombreCompleto("María López").habilitado(true).build()));

        controller.event("s1", joinEvent(), principal("admin"), headers("socket-a"));
        controller.event("s1", joinEvent(), principal("maria"), headers("socket-b"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messaging, atLeastOnce()).convertAndSend(eq("/topic/uml/s1"), payload.capture());
        UmlCollaborationEvent joined = (UmlCollaborationEvent) payload.getValue();
        List<UmlCollaborationWebSocketController.Participant> participants = participants(joined);
        assertEquals(2, participants.size());
        assertTrue(participants.stream().anyMatch(item -> "Andrés Pérez".equals(item.userName()) && item.host()));
        assertTrue(participants.stream().anyMatch(item -> "María López".equals(item.userName()) && !item.host()));

        SessionDisconnectEvent disconnect = mock(SessionDisconnectEvent.class);
        when(disconnect.getSessionId()).thenReturn("socket-b");
        controller.disconnected(disconnect);

        verify(locks).releaseBySocket("socket-b");
        verify(messaging, atLeastOnce()).convertAndSend(eq("/topic/uml/s1"), payload.capture());
        UmlCollaborationEvent left = (UmlCollaborationEvent) payload.getValue();
        List<UmlCollaborationWebSocketController.Participant> remaining = participants(left);
        assertEquals(List.of("admin"), remaining.stream().map(UmlCollaborationWebSocketController.Participant::userId).toList());
    }

    private UmlCollaborationEvent joinEvent() {
        return UmlCollaborationEvent.builder().type("session:join").projectId("p1").build();
    }

    private Principal principal(String username) {
        return () -> username;
    }

    private SimpMessageHeaderAccessor headers(String socketId) {
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);
        when(headers.getSessionId()).thenReturn(socketId);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private List<UmlCollaborationWebSocketController.Participant> participants(UmlCollaborationEvent event) {
        return (List<UmlCollaborationWebSocketController.Participant>) event.getData().get("participants");
    }
}
