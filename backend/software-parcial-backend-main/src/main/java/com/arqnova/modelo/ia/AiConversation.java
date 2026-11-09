package com.arqnova.modelo.ia;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.arqnova.modelo.dto.response.UmlAiOperationResponse;

/** Application-owned conversation memory; independent of provider sessions and persistence. */
public final class AiConversation {
    private AiConversation() {}
    public enum Mode { ASK, REVIEW, MODEL, ADVISE, MEDIATE }
    public enum State { PENDING, COMPLETED, FAILED }
    public record Finding(String severity, String elementId, String message) {}
    public record Answer(String text, List<Finding> findings, String provider, String model, List<UmlAiOperationResponse> operations) {
        public Answer(String text,List<Finding> findings,String provider,String model){this(text,findings,provider,model,List.of());}
        public Answer { findings = findings == null ? List.of() : List.copyOf(findings); operations=operations==null?List.of():List.copyOf(operations); }
    }
    public record Turn(String requestId, String projectId, String authorId, String authorName,
                       Mode mode, String instruction, State state, Answer answer, String errorCode,
                       int diagramVersion, Instant createdAt, Instant updatedAt) {
        public Turn finish(Answer response, Instant now) {
            return new Turn(requestId,projectId,authorId,authorName,mode,instruction,State.COMPLETED,response,null,diagramVersion,createdAt,now);
        }
        public Turn fail(String code, Instant now) {
            return new Turn(requestId,projectId,authorId,authorName,mode,instruction,State.FAILED,null,code,diagramVersion,createdAt,now);
        }
    }
    public record Page(List<Turn> turns, String nextCursor) {
        public Page { turns = List.copyOf(turns); }
    }
    public record Context(String projectId, String projectName, String authorId, Map<String,Object> diagram,
                          int diagramVersion, List<Turn> groupHistory, List<Turn> userHistory) {}
    public record Submission(Turn turn, boolean created) {}
    public record Change(String projectId, String requestId, State state,Mode mode,int diagramVersion) {
        public Change(String projectId,String requestId,State state){this(projectId,requestId,state,null,0);}
    }
}
