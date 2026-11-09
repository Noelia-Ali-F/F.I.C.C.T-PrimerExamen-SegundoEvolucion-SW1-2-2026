package com.arqnova.servicios.puertos.salida;
import com.arqnova.modelo.entidades.*;
import java.util.*;
public interface UmlCollaborationActivityDataPort extends EntityDataPort<UmlCollaborationActivityEntity,String> {
 List<UmlCollaborationActivityEntity> findTop100BySessionIdOrderByOccurredAtDesc(String sessionId);
}
