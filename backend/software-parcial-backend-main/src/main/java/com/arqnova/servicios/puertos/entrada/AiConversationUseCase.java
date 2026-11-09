package com.arqnova.servicios.puertos.entrada;
import com.arqnova.modelo.ia.AiConversation.*;
public interface AiConversationUseCase {
    boolean enabled();
    default void recoverPending() {}
    default boolean modelingEnabled(){return false;}
    boolean canModel(String projectId, String username);
    Page history(String projectId, String username, String cursor, int limit);
    Turn get(String projectId, String username, String requestId);
    Turn send(String projectId, String username, String requestId, Mode mode, String instruction);
}
