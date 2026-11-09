package com.arqnova.controladores.configuracion;

import com.arqnova.servicios.puertos.entrada.TokenValidationUseCase;
import lombok.RequiredArgsConstructor;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String AI_TOPIC = "/topic/ai/";
    private final Map<String, TokenValidationUseCase.Claims> sessions = new ConcurrentHashMap<>();

    private final TokenValidationUseCase authTokenService;
    private final com.arqnova.servicios.puertos.entrada.UserQueryUseCase usuarioRepository;
    private final com.arqnova.servicios.puertos.entrada.ProjectUseCase projects;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authorization = accessor.getFirstNativeHeader("Authorization");
                    if (authorization == null || !authorization.startsWith("Bearer ")) {
                        throw new MessagingException("Autenticación WebSocket requerida");
                    }
                    try {
                        TokenValidationUseCase.Claims claims = authTokenService.verificar(authorization.substring(7));
                        com.arqnova.modelo.Usuario usuario = usuarioRepository.findByUsername(claims.username())
                                .orElseThrow(() -> new MessagingException("Usuario WebSocket inexistente"));
                        if (Boolean.FALSE.equals(usuario.getHabilitado())) {
                            throw new MessagingException("Usuario WebSocket deshabilitado");
                        }
                        if (usuario.getRol() == null || !usuario.getRol().name().equalsIgnoreCase(claims.rol())) {
                            throw new MessagingException("Los permisos WebSocket ya no son válidos");
                        }
                        accessor.setUser(() -> claims.username());
                        sessions.put(accessor.getSessionId(), claims);
                    } catch (RuntimeException ex) {
                        throw new MessagingException("Token WebSocket inválido", ex);
                    }
                }
                if (accessor.getUser() == null && !StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                    throw new MessagingException("Sesión WebSocket no autenticada");
                }
                String destination = accessor.getDestination();
                if (StompCommand.SEND.equals(accessor.getCommand()) && destination != null && destination.startsWith("/topic")) {
                    throw new MessagingException("Solo el servidor puede publicar en el broker");
                }
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand()) && destination != null && destination.startsWith("/topic")) {
                    // A wildcard subscription could bypass the authorization of a concrete AI topic.
                    if (destination.indexOf('*') >= 0 || destination.indexOf('?') >= 0 || destination.indexOf('{') >= 0) {
                        throw new MessagingException("Suscríbete a un destino concreto");
                    }
                    if (destination.startsWith(AI_TOPIC)) {
                        requireAiAccess(destination, sessions.get(accessor.getSessionId()));
                    }
                }
                return message;
            }
        });
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ExecutorChannelInterceptor() {
            @Override
            public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
                var headers = message.getHeaders();
                String destination = SimpMessageHeaderAccessor.getDestination(headers);
                if (SimpMessageType.MESSAGE != SimpMessageHeaderAccessor.getMessageType(headers)
                        || destination == null || !destination.startsWith(AI_TOPIC)) return message;

                // The simple broker supplies the recipient session ID, not the recipient principal.
                String sessionId = SimpMessageHeaderAccessor.getSessionId(headers);
                try {
                    requireAiAccess(destination, sessionId == null ? null : sessions.get(sessionId));
                    return message;
                } catch (RuntimeException unavailableOrDenied) {
                    return null;
                }
            }
        });
    }

    @EventListener
    public void removeDisconnectedSession(SessionDisconnectEvent event) {
        sessions.remove(event.getSessionId());
    }

    private void requireAiAccess(String destination, TokenValidationUseCase.Claims claims) {
        if (claims == null || claims.expiresAt() < Instant.now().getEpochSecond()) {
            throw new MessagingException("Sesión WebSocket vencida o no autenticada");
        }
        var user = usuarioRepository.findByUsername(claims.username())
                .orElseThrow(() -> new MessagingException("Usuario WebSocket inexistente"));
        if (Boolean.FALSE.equals(user.getHabilitado()) || user.getRol() == null
                || !user.getRol().name().equalsIgnoreCase(claims.rol())) {
            throw new MessagingException("Los permisos WebSocket ya no son válidos");
        }
        projects.requireAccess(destination.substring(AI_TOPIC.length()), claims.username());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-collab")
                .setAllowedOriginPatterns("*");
    }
}
