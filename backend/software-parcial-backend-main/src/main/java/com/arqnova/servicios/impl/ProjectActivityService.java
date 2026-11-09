package com.arqnova.servicios.impl;
import com.arqnova.modelo.ia.ProjectActivity;
import com.arqnova.modelo.ia.ProjectActivity.Page;
import com.arqnova.servicios.puertos.entrada.*;
import com.arqnova.servicios.puertos.salida.UmlCollaborationActivityDataPort;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public final class ProjectActivityService implements ProjectActivityUseCase {
    private final ProjectUseCase projects;private final UmlCollaborationActivityDataPort activities;private final ZoneId zone;
    public ProjectActivityService(ProjectUseCase projects,UmlCollaborationActivityDataPort activities,ZoneId zone){this.projects=projects;this.activities=activities;this.zone=zone;}
    public Page history(String project,String user,String cursor,int limit){
        projects.requireAccess(project,user);if(limit<1||limit>100)throw new IllegalArgumentException("Límite inválido");
        LocalDateTime before=null;String beforeId=null;
        if(cursor!=null&&!cursor.isBlank())try{
            if(cursor.length()>512)throw new IllegalArgumentException();
            String raw=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.UTF_8);int split=raw.indexOf('|');
            before=LocalDateTime.parse(raw.substring(0,split));beforeId=raw.substring(split+1);
            if(beforeId.isBlank()||beforeId.length()>64)throw new IllegalArgumentException();
        }catch(RuntimeException e){throw new IllegalArgumentException("Cursor de actividad inválido");}
        var rows=activities.projectHistory(project,before,beforeId,limit+1);var visible=rows.stream().limit(limit).toList();
        String next=null;if(rows.size()>limit){var last=visible.get(visible.size()-1);next=Base64.getUrlEncoder().withoutPadding().encodeToString((last.getOccurredAt()+"|"+last.getEventId()).getBytes(StandardCharsets.UTF_8));}
        return new Page(visible.stream().map(v->new ProjectActivity(v.getEventId(),v.getUserName(),v.getEventType(),v.getOperation(),v.getOccurredAt().atZone(zone).toInstant().toEpochMilli())).toList(),next);
    }
}
