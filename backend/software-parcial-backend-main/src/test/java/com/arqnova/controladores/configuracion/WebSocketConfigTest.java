package com.arqnova.controladores.configuracion;

import com.arqnova.modelo.Usuario;
import com.arqnova.modelo.enums.RolUsuario;
import com.arqnova.modelo.excepciones.UnauthorizedActionException;
import com.arqnova.servicios.puertos.entrada.ProjectUseCase;
import com.arqnova.servicios.puertos.entrada.TokenValidationUseCase;
import com.arqnova.servicios.puertos.entrada.UserQueryUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketConfigTest {
    private static final String TOPIC = "/topic/ai/project";
    private final TokenValidationUseCase tokens = mock(TokenValidationUseCase.class);
    private final UserQueryUseCase users = mock(UserQueryUseCase.class);
    private final ProjectUseCase projects = mock(ProjectUseCase.class);
    private final WebSocketConfig config = new WebSocketConfig(tokens, users, projects);
    private final ExecutorSubscribableChannel inbound = new ExecutorSubscribableChannel();
    private final ExecutorSubscribableChannel outbound = new ExecutorSubscribableChannel();
    private final ExecutorSubscribableChannel server = new ExecutorSubscribableChannel();
    private final List<Message<?>> received = new ArrayList<>();

    WebSocketConfigTest() {
        config.configureClientInboundChannel(registrationFor(inbound));
        config.configureClientOutboundChannel(registrationFor(outbound));
        outbound.subscribe(received::add);
        new SimpleBrokerMessageHandler(inbound, outbound, server, List.of("/topic")).start();
    }

    @Test
    void blocksClientPublishingAndWildcardSubscriptions() {
        connect("ana");
        assertThrows(MessagingException.class, () -> client(StompCommand.SEND, "ana", TOPIC));
        assertThrows(MessagingException.class, () -> client(StompCommand.SEND, "ana", "/topic/uml/session"));
        assertThrows(MessagingException.class, () -> client(StompCommand.SUBSCRIBE, "ana", "/topic/**"));
        assertDoesNotThrow(() -> client(StompCommand.SEND, "ana", "/app/uml/events"));
        assertTrue(deliveries().isEmpty());
    }

    @Test
    void onlyStillAuthorizedRecipientsReceiveTheNextBrokerEvent() {
        connect("ana");
        connect("luis");
        client(StompCommand.SUBSCRIBE, "ana", TOPIC);
        client(StompCommand.SUBSCRIBE, "luis", TOPIC);

        publish();
        assertEquals(List.of("ana", "luis"), deliveries());

        when(projects.requireAccess("project", "ana")).thenThrow(new UnauthorizedActionException("Revocada"));
        received.clear();
        publish();
        assertEquals(List.of("luis"), deliveries());
        assertThrows(MessagingException.class, () -> client(StompCommand.SUBSCRIBE, "ana", TOPIC));
    }

    private ChannelRegistration registrationFor(ExecutorSubscribableChannel channel) {
        return new ChannelRegistration() {
            @Override
            public ChannelRegistration interceptors(org.springframework.messaging.support.ChannelInterceptor... interceptors) {
                for (var interceptor : interceptors) channel.addInterceptor(interceptor);
                return this;
            }
        };
    }

    private void connect(String user) {
        var account = new Usuario();
        account.setRol(RolUsuario.SOLICITANTE);
        account.setHabilitado(true);
        when(users.findByUsername(user)).thenReturn(Optional.of(account));
        when(tokens.verificar(user)).thenReturn(new TokenValidationUseCase.Claims(
                user, "SOLICITANTE", "", Instant.now().plusSeconds(60).getEpochSecond()));
        client(StompCommand.CONNECT, user, null);
    }

    private void client(StompCommand command, String user, String destination) {
        var headers = StompHeaderAccessor.create(command);
        headers.setSessionId(user);
        if (command == StompCommand.CONNECT) headers.setNativeHeader("Authorization", "Bearer " + user);
        else headers.setUser(() -> user);
        if (destination != null) headers.setDestination(destination);
        if (command == StompCommand.SUBSCRIBE) headers.setSubscriptionId("ai");
        headers.setLeaveMutable(true);
        inbound.send(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()));
    }

    private void publish() {
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination(TOPIC);
        server.send(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()));
    }

    private List<String> deliveries() {
        return received.stream().filter(message ->
                TOPIC.equals(SimpMessageHeaderAccessor.getDestination(message.getHeaders())))
                .map(message -> SimpMessageHeaderAccessor.getSessionId(message.getHeaders())).sorted().toList();
    }
}
