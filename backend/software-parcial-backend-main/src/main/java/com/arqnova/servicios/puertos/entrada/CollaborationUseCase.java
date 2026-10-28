package com.arqnova.servicios.puertos.entrada;
import com.arqnova.modelo.UmlCollaborationSession;
import com.arqnova.modelo.dto.request.CreateUmlSessionRequest;
import com.arqnova.modelo.dto.request.UmlCollaborationEvent;
import com.arqnova.modelo.dto.response.UmlSessionResponse;
import com.arqnova.modelo.excepciones.UnauthorizedActionException;
import com.arqnova.servicios.puertos.salida.CollaborationSessionStore;
import com.arqnova.modelo.entidades.ProjectMemberRole;
import com.arqnova.servicios.puertos.salida.UmlDiagramDataPort;
import com.arqnova.servicios.puertos.salida.UmlCollaborationActivityDataPort;
import com.arqnova.modelo.entidades.UmlCollaborationActivityEntity;
import com.arqnova.servicios.persistencia.UmlProjectPersistenceService;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
public interface CollaborationUseCase {
 UmlSessionResponse start(CreateUmlSessionRequest request, String username) ;
 UmlSessionResponse join(String inviteCode, String username) ;
 UmlCollaborationSession requireSession(String sessionId, String projectId) ;
 void apply(UmlCollaborationEvent event) ;
 void recordActivity(UmlCollaborationEvent event) ;
 List<UmlCollaborationEvent> activities(String sessionId, String username) ;

}
