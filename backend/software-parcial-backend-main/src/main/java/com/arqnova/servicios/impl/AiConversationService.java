package com.arqnova.servicios.impl;

import com.arqnova.modelo.ia.AiConversation.*;
import com.arqnova.modelo.entidades.ProjectMemberRole;
import java.util.regex.Pattern;
import com.arqnova.modelo.excepciones.*;
import com.arqnova.servicios.puertos.entrada.*;
import com.arqnova.servicios.puertos.salida.*;
import java.time.*;
import java.util.*;

/** New conversation use cases. Existing UML editing and image interpretation are unchanged. */
public final class AiConversationService implements AiConversationUseCase {
    private static final String UML_ACTION = "(?:crea(?:r|s)?|gener(?:a|ar|es)|agreg(?:a|ar|ues)|añad(?:e|ir|as)|anad(?:e|ir|as)|"
            + "elimin(?:a|ar|es)|borr(?:a|ar|es)|quit(?:a|ar|es)|actualiz(?:a|ar|es)|modific(?:a|ar|ues)|"
            + "cambi(?:a|ar|es)|corrig(?:e|ir|as)|corregir|renombr(?:a|ar|es)|relacion(?:a|ar|es)|asoci(?:a|ar|es)|"
            + "conect(?:a|ar|es)|establ(?:ece|ecer|ezcas)|defin(?:e|ir|as)|coloc(?:a|ar)|coloques|pon(?:er|gas)?|"
            + "ajust(?:a|ar|es)|edit(?:a|ar|es)|incorpor(?:a|ar|es)|muev(?:e|as)|mover|haz(?:me)?|haceme)";
    private static final Pattern DIRECT_MODELING = Pattern.compile(
            "^(?:por favor[, ]+)?(?:quiero que |necesito que |te pido que |(?:revisa y ))?" + UML_ACTION + "\\s+\\S",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern POLITE_MODELING = Pattern.compile(
            "^¿?(?:puedes|podrías|podrias)\\s+" + UML_ACTION + "\\s+\\S[^?¿]*[?]?$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern UNCERTAIN_MODELING = Pattern.compile(
            "\\b(?:si (?:conviene|te parece|crees|es necesario|hace falta)|(?:lo|la) que (?:quieras|prefieras)|"
                    + "algo(?=\\s*[.!?]*$)|o bien|tal vez|quizás|quizas|podríamos|podriamos)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private final AiMemoryPort memory;
    private final AiConversationProviderPort provider;
    private final AiConversationEventsPort events;
    private final AiTaskPort tasks;
    private final ProjectUseCase projects;
    private final UserQueryUseCase users;
    private final Clock clock;
    private final AiModelMutationPort mutations;
    private final Object[] mutationGuards=java.util.stream.IntStream.range(0,64).mapToObj(i->new Object()).toArray();
    public AiConversationService(AiMemoryPort memory, AiConversationProviderPort provider, AiConversationEventsPort events,
            AiTaskPort tasks, ProjectUseCase projects, UserQueryUseCase users, Clock clock) {
        this(memory,provider,events,tasks,projects,users,clock,null);
    }
    public AiConversationService(AiMemoryPort memory,AiConversationProviderPort provider,AiConversationEventsPort events,AiTaskPort tasks,
            ProjectUseCase projects,UserQueryUseCase users,Clock clock,AiModelMutationPort mutations){
        this.memory=memory;this.provider=provider;this.events=events;this.tasks=tasks;this.projects=projects;this.users=users;this.clock=clock;this.mutations=mutations;
    }
    public boolean enabled() { return memory.enabled(); }
    public boolean modelingEnabled(){return enabled()&&mutations!=null&&mutations.enabled();}
    @Override public boolean canModel(String project, String user) {
        var role = projects.requireAccess(project, user);
        return modelingEnabled() && (role == ProjectMemberRole.OWNER || role == ProjectMemberRole.EDITOR);
    }
    public Page history(String project, String user, String cursor, int limit) {
        access(project,user); available();
        if(limit<1||limit>50)throw new IllegalArgumentException("El límite debe estar entre 1 y 50");
        Page page=memory.history(project,cursor,limit);
        return new Page(page.turns().stream().map(this::expire).toList(),page.nextCursor());
    }
    public Turn get(String project,String user,String requestId) {
        access(project,user);available();
        return expire(memory.find(project,requestId).orElseThrow(()->new ResourceNotFoundException("Interacción IA","id",requestId)));
    }
    public Turn send(String project,String user,String requestId,Mode mode,String instruction) {
        access(project,user);available();
        if(requestId==null||!requestId.matches("[A-Za-z0-9_-]{8,100}"))throw new IllegalArgumentException("Identificador de solicitud inválido");
        if(mode==null||instruction==null||instruction.isBlank()||instruction.length()>4000)throw new IllegalArgumentException("Escribe un mensaje de hasta 4000 caracteres");
        if(mode==Mode.MODEL){if(!modelingEnabled())throw new IllegalArgumentException("El modelado compartido todavía no está habilitado");projects.requireAccess(project,user,com.arqnova.modelo.entidades.ProjectMemberRole.OWNER,com.arqnova.modelo.entidades.ProjectMemberRole.EDITOR);}
        var author=users.findByUsername(user).filter(v->!Boolean.FALSE.equals(v.getHabilitado()))
                .orElseThrow(()->new UnauthorizedActionException("Usuario no disponible"));
        int version=projects.findDiagramByProjectId(project).map(v->v.getModelVersion()).orElse(0);
        Instant now=clock.instant();
        var pending=new Turn(requestId,project,user,author.getNombreCompleto()==null?user:author.getNombreCompleto(),mode,
                instruction.trim(),State.PENDING,null,null,version,now,now);
        Submission submission=memory.begin(pending);
        Turn turn=submission.turn();
        if(!turn.authorId().equals(user)||turn.mode()!=mode||!turn.instruction().equals(instruction.trim()))
            throw new DuplicateResourceException("Ese identificador ya corresponde a otra solicitud");
        if(!submission.created())return expire(turn);
        announce(turn);
        try { tasks.schedule(()->process(turn)); }
        catch(RuntimeException failure) { finish(turn.fail("AI_QUEUE_BUSY",clock.instant())); }
        return turn;
    }
    private void process(Turn turn) {
        try {
            if(memory.find(turn.projectId(),turn.requestId()).map(v->v.state()!=State.PENDING).orElse(true))return;
            if(!turn.createdAt().plusSeconds(180).isAfter(clock.instant())){expire(turn);return;}
            access(turn.projectId(),turn.authorId());
            if(turn.mode()==Mode.MODEL){
                projects.requireAccess(turn.projectId(),turn.authorId(),com.arqnova.modelo.entidades.ProjectMemberRole.OWNER,com.arqnova.modelo.entidades.ProjectMemberRole.EDITOR);
                var applied=mutations.find(turn);if(applied.isPresent()){finish(appliedTurn(turn,applied.get()));return;}
            }
            if(users.findByUsername(turn.authorId()).filter(v->!Boolean.FALSE.equals(v.getHabilitado())).isEmpty())throw new UnauthorizedActionException("Usuario deshabilitado");
            if (turn.mode() == Mode.MODEL && !explicitModeling(turn.instruction())) {
                finish(turn.finish(new Answer(
                        "Confirma qué operación UML deseas aplicar e indica el elemento y los datos concretos. "
                        + "Para evaluar opciones, usa Consulta, Asesoramiento, Revisión o Mediación.",
                        List.of(), "LOCAL", "modeling-clarification"), clock.instant()));
                return;
            }
            var project=projects.findProjectById(turn.projectId()).orElseThrow();
            var diagram=projects.findDiagramByProjectId(turn.projectId());
            var recent = memory.history(turn.projectId(), null, 30).turns();
            var groupHistory = turn.mode() == Mode.MEDIATE
                    ? recent.stream().filter(other -> !other.requestId().equals(turn.requestId())).limit(12).toList()
                    : completed(recent, 12);
            var context=new Context(turn.projectId(),project.getName(),turn.authorId(),diagram.map(v->v.getModel()).orElse(Map.of("classes",List.of(),"relations",List.of())),
                    diagram.map(v->v.getModelVersion()).orElse(0),groupHistory,
                    completed(memory.recentByUser(turn.projectId(),turn.authorId(),20),8));
            Answer answer=provider.answer(turn,context);
            if(answer==null||answer.text()==null||answer.text().isBlank()||answer.text().length()>12000||answer.findings().size()>30)
                throw new IllegalArgumentException("Respuesta IA inválida");
            if(turn.mode()!=Mode.MODEL&&!answer.operations().isEmpty())throw new IllegalArgumentException("Una consulta no puede modificar el modelo");
            if(turn.mode()==Mode.MODEL&&!answer.operations().isEmpty()){
                synchronized(guard(turn)){
                    var current=memory.find(turn.projectId(),turn.requestId()).orElseThrow();
                    if(expire(current).state()!=State.PENDING)return;
                    if(users.findByUsername(turn.authorId()).filter(v->!Boolean.FALSE.equals(v.getHabilitado())).isEmpty())throw new UnauthorizedActionException("Usuario deshabilitado");
                    finish(appliedTurn(turn,mutations.apply(turn,context,answer)));
                }return;
            }
            // Record the model version actually reviewed, which may have advanced while the job waited.
            var reviewed=new Turn(turn.requestId(),turn.projectId(),turn.authorId(),turn.authorName(),turn.mode(),turn.instruction(),turn.state(),null,null,
                    context.diagramVersion(),turn.createdAt(),turn.updatedAt());
            finish(reviewed.finish(answer,clock.instant()));
        } catch(Exception error) {
            if(turn.mode()==Mode.MODEL){var applied=mutations.find(turn);if(applied.isPresent()){finish(appliedTurn(turn,applied.get()));return;}}
            String code=error instanceof AiProviderException p&&p.httpStatus==429?"AI_RATE_LIMITED":error instanceof UnauthorizedActionException?"ACCESS_CHANGED":
                    error instanceof DuplicateResourceException?"AI_MODEL_CONFLICT":
                    error instanceof IllegalArgumentException&&"AI_CONTEXT_TOO_LARGE".equals(error.getMessage())?"AI_CONTEXT_TOO_LARGE":
                    turn.mode()==Mode.MODEL&&error instanceof IllegalArgumentException?"AI_INVALID_OPERATIONS":"AI_PROVIDER_UNAVAILABLE";
            finish(turn.fail(code,clock.instant()));
        }
    }
    private boolean explicitModeling(String instruction) {
        return !UNCERTAIN_MODELING.matcher(instruction).find()
                && (POLITE_MODELING.matcher(instruction).matches()
                    || (instruction.indexOf('?') < 0 && instruction.indexOf('¿') < 0
                        && DIRECT_MODELING.matcher(instruction).find()));
    }
    private List<Turn> completed(List<Turn> turns,int limit) { return turns.stream().filter(v->v.state()==State.COMPLETED).limit(limit).toList(); }
    private Turn expire(Turn turn) {
        synchronized(guard(turn)){return expireLocked(turn);}
    }
    private Object guard(Turn turn){return mutationGuards[Math.floorMod(Objects.hash(turn.projectId(),turn.requestId()),mutationGuards.length)];}
    private Turn expireLocked(Turn turn) {
        if(turn.state()==State.PENDING&&turn.mode()==Mode.MODEL&&mutations!=null){
            var applied=mutations.find(turn);if(applied.isPresent()){
                Turn recovered=appliedTurn(turn,applied.get());if(memory.finish(recovered))announce(recovered);
                return memory.find(turn.projectId(),turn.requestId()).orElse(recovered);
            }
        }
        if(turn.state()!=State.PENDING||turn.createdAt().plusSeconds(180).isAfter(clock.instant()))return turn;
        Turn expired=turn.fail("AI_REQUEST_INTERRUPTED",clock.instant());
        if(memory.finish(expired)){announce(expired);return expired;}
        return memory.find(turn.projectId(),turn.requestId()).orElse(turn);
    }
    private void finish(Turn turn) { if(memory.finish(turn))announce(turn); }
    private Turn appliedTurn(Turn turn,AiModelMutationPort.Applied applied){return new Turn(turn.requestId(),turn.projectId(),turn.authorId(),turn.authorName(),turn.mode(),turn.instruction(),State.COMPLETED,applied.answer(),null,applied.diagramVersion(),turn.createdAt(),clock.instant());}
    private void announce(Turn turn) {
        try { events.changed(new Change(turn.projectId(),turn.requestId(),turn.state(),turn.mode(),turn.diagramVersion())); }
        catch(RuntimeException unavailable) { /* Durable history is authoritative; clients can recover on reconnect. */ }
    }
    private void access(String project,String user) { projects.requireAccess(project,user); }
    private void available() { if(!memory.enabled())throw new AiMemoryUnavailableException(); }
}
