package com.arqnova.servicios.puertos.salida;
import com.arqnova.modelo.ia.AiConversation.*;
import java.util.*;
public interface AiMemoryPort {
    boolean enabled();
    Submission begin(Turn turn);
    Optional<Turn> find(String projectId, String requestId);
    Page history(String projectId, String cursor, int limit);
    List<Turn> recentByUser(String projectId, String username, int limit);
    boolean finish(Turn turn);
}
