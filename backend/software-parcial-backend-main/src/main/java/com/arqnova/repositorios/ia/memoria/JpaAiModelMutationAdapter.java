package com.arqnova.repositorios.ia.memoria;

import com.arqnova.modelo.ia.AiConversation.*;
import com.arqnova.modelo.excepciones.*;
import com.arqnova.servicios.impl.UmlMutationPlanner;
import com.arqnova.servicios.puertos.entrada.*;
import com.arqnova.servicios.puertos.salida.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** PostgreSQL receipt and diagram are committed together; DynamoDB remains conversation memory. */
@Component @Profile("!dynamodb")
public class JpaAiModelMutationAdapter implements AiModelMutationPort {
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final ProjectUseCase projects;
    private final UmlDiagramDataPort diagrams;private final CollaborationSessionStore sessions;private final ElementLockUseCase locks;
    private final TransactionTemplate tx;private final boolean enabled;
    private final UmlMutationPlanner planner=new UmlMutationPlanner();
    public JpaAiModelMutationAdapter(JdbcTemplate jdbc,ObjectMapper json,ProjectUseCase projects,UmlDiagramDataPort diagrams,
            CollaborationSessionStore sessions,ElementLockUseCase locks,PlatformTransactionManager manager,
            @Value("${app.ai.conversations.modeling-enabled:false}") boolean enabled){
        this.jdbc=jdbc;this.json=json;this.projects=projects;this.diagrams=diagrams;this.sessions=sessions;this.locks=locks;this.tx=new TransactionTemplate(manager);this.enabled=enabled;
    }
    public boolean enabled(){return enabled;}
    public Optional<Applied> find(Turn turn){
        var rows=jdbc.query("SELECT author_id,instruction,answer::text,diagram_version FROM ai_model_mutation_receipts WHERE project_id=? AND request_id=?",
                (r,n)->{
                    if(!turn.authorId().equals(r.getString(1))||!turn.instruction().equals(r.getString(2)))throw new DuplicateResourceException("Identificador de modificación ya utilizado");
                    try{return new Applied(json.readValue(r.getString(3),Answer.class),r.getInt(4));}catch(Exception e){throw new IllegalStateException("Comprobante UML inválido",e);}
                },turn.projectId(),turn.requestId());
        return rows.stream().findFirst();
    }
    public Applied apply(Turn turn,Context context,Answer answer){
        if(!enabled)throw new IllegalArgumentException("La edición compartida todavía no está habilitada");
        if(turn.mode()!=Mode.MODEL||!turn.projectId().equals(context.projectId()))throw new IllegalArgumentException("Solicitud de modelado inválida");
        // The existing lock implementation synchronizes acquire/release on this same object.
        // Keep its monitor through SQL commit so an editor cannot acquire an element mid-commit.
        synchronized(locks){return tx.execute(status->{
            projects.requireWriteAccess(turn.projectId(),turn.authorId());
            var diagram=diagrams.findByProjectIdForUpdate(turn.projectId()).orElseThrow(()->new IllegalArgumentException("Falta el diagrama guardado"));
            var previous=find(turn);if(previous.isPresent())return previous.get();
            if(diagram.getModelVersion()!=context.diagramVersion())throw new DuplicateResourceException("El diagrama cambió durante el análisis de IA");
            var plan=planner.plan(diagram.getModel(),answer.operations(),turn.requestId());
            sessions.findActiveByProjectId(turn.projectId()).ifPresent(session->{
                var elements=new LinkedHashSet<>(plan.affected());
                // A class mutation must also respect an attribute editor's existing lock.
                Object nodes=diagram.getModel().get("classes");
                if(nodes instanceof List<?> list)for(Object item:list)if(item instanceof Map<?,?> node&&elements.contains(new UmlMutationPlanner.Element("class",String.valueOf(node.get("id"))))) {
                    if(node.get("attributes") instanceof List<?> attrs)for(Object raw:attrs)if(raw instanceof Map<?,?> attr)elements.add(new UmlMutationPlanner.Element("attribute",String.valueOf(attr.get("id"))));
                }
                for(var element:elements)if(locks.find(session.getId(),element.type(),element.id()).isPresent())throw new DuplicateResourceException("Hay un elemento en edición. Finaliza esa edición antes de modelar con IA");
            });
            var before=diagram.getModel();
            boolean changed = !before.equals(plan.model());
            int version = diagram.getModelVersion();
            Answer confirmed = answer;
            if (changed) {
                version = projects.saveDiagram(diagram.getId(),turn.projectId(),plan.model(),context.diagramVersion()).getModelVersion();
                projects.recordAiChanges(turn.projectId(),before,plan.model(),turn.authorId(),turn.requestId(),answer.provider(),turn.instruction());
            } else {
                confirmed = new Answer("La solicitud se procesó. El UML ya coincide con el resultado solicitado.",
                        answer.findings(),answer.provider(),answer.model());
            }
            try{jdbc.update("INSERT INTO ai_model_mutation_receipts(project_id,request_id,author_id,instruction,answer,diagram_version) VALUES(?,?,?,?,CAST(? AS JSONB),?)",
                    turn.projectId(),turn.requestId(),turn.authorId(),turn.instruction(),json.writeValueAsString(confirmed),version);}
            catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("Respuesta UML inválida",e);}
            return new Applied(confirmed,version);
        });}
    }
}
