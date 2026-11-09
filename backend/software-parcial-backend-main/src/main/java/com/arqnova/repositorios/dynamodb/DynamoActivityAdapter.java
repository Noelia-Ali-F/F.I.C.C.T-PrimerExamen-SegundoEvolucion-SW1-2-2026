package com.arqnova.repositorios.dynamodb;

import com.arqnova.modelo.entidades.UmlCollaborationActivityEntity;
import com.arqnova.servicios.puertos.salida.UmlCollaborationActivityDataPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository @Profile("dynamodb")
public class DynamoActivityAdapter extends DynamoEntityAdapter<UmlCollaborationActivityEntity, String> implements UmlCollaborationActivityDataPort {
    public DynamoActivityAdapter(DynamoDocumentStore store) { super(store, "ACTIVITY", UmlCollaborationActivityEntity.class, UmlCollaborationActivityEntity::getEventId, v -> v); }
    protected void prepare(UmlCollaborationActivityEntity v, UmlCollaborationActivityEntity old) {
        store.requireParent("PROJECT", v.getProjectId()); if (v.getSessionId() != null) store.requireParent("SESSION", v.getSessionId());
    }
    public List<UmlCollaborationActivityEntity> findTop100BySessionIdOrderByOccurredAtDesc(String id) {
        return findAll().stream().filter(v -> id.equals(v.getSessionId())).sorted(Comparator.comparing(UmlCollaborationActivityEntity::getOccurredAt).reversed()).limit(100).toList();
    }
    public List<UmlCollaborationActivityEntity> projectHistory(String project,java.time.LocalDateTime before,String beforeId,int limit){
        return findAll().stream().filter(v->project.equals(v.getProjectId()))
            .filter(v->before==null||v.getOccurredAt().isBefore(before)||(v.getOccurredAt().equals(before)&&v.getEventId().compareTo(beforeId)<0))
            .sorted(Comparator.comparing(UmlCollaborationActivityEntity::getOccurredAt).thenComparing(UmlCollaborationActivityEntity::getEventId).reversed()).limit(limit).toList();
    }
}
