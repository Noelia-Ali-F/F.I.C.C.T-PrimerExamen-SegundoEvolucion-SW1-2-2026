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
    default void ensurePending(Turn turn) {}
    default boolean durableJobs() { return false; }
    default JobPage pendingJobs(String cursor, int limit) { return new JobPage(List.of(), null); }
    default Optional<Job> claim(Turn turn, String owner, long now, long until) {
        return Optional.of(new Job(turn, owner, 1, null));
    }
    default boolean renew(Job job, long now, long until) { return true; }
    default boolean stage(Job job, Turn result, long now) { return true; }
    default boolean complete(Job job, Turn result, long now) { return finish(result); }
    default void release(Job job) {}

}
