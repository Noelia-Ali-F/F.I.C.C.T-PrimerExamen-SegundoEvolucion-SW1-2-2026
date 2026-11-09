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
    private static final long LEASE_SECONDS = 120;
    private static final int MAX_ATTEMPTS = 3;
    private final String worker = UUID.randomUUID().toString();
    private String recoveryCursor;
    private final Map<String,Turn> resultsAwaitingPersistence = new java.util.concurrent.ConcurrentHashMap<>();
    private String resultKey(Turn turn) { return turn.projectId() + "\u0000" + turn.requestId(); }
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiConversationService.class);
    private final AiMemoryPort memory;
    private final AiConversationProviderPort provider;
    private final AiConversationEventsPort events;
    private final AiTaskPort tasks;
    private final ProjectUseCase projects;
    private final UserQueryUseCase users;
    private final Clock clock;
    private final AiModelMutationPort mutations;
    private final AiContextHistory contextHistory;
    private final Object[] mutationGuards=java.util.stream.IntStream.range(0,64).mapToObj(i->new Object()).toArray();
    public AiConversationService(AiMemoryPort memory, AiConversationProviderPort provider, AiConversationEventsPort events,
            AiTaskPort tasks, ProjectUseCase projects, UserQueryUseCase users, Clock clock) {
        this(memory,provider,events,tasks,projects,users,clock,null);
    }
    public AiConversationService(AiMemoryPort memory,AiConversationProviderPort provider,AiConversationEventsPort events,AiTaskPort tasks,
            ProjectUseCase projects,UserQueryUseCase users,Clock clock,AiModelMutationPort mutations){
        this(memory, provider, events, tasks, projects, users, clock, mutations, null);
    }
    public AiConversationService(AiMemoryPort memory, AiConversationProviderPort provider, AiConversationEventsPort events,
            AiTaskPort tasks, ProjectUseCase projects, UserQueryUseCase users, Clock clock, AiModelMutationPort mutations,
            UmlCollaborationActivityDataPort activities) {
        this.contextHistory = new AiContextHistory(memory, activities);
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
        if(!submission.created()) {
            if (turn.state() == State.PENDING && memory.durableJobs()) enqueue(turn);
            return expire(turn);
        }
        announce(turn);
        enqueue(turn);
        return turn;
    }
    @Override public synchronized void recoverPending() {
        if (!memory.enabled() || !memory.durableJobs()) return;
        try {
            for (var buffered : resultsAwaitingPersistence.values().stream().limit(20).toList()) {
                if (memory.find(buffered.projectId(),buffered.requestId()).map(t -> t.state()!=State.PENDING).orElse(false)) {
                    resultsAwaitingPersistence.remove(resultKey(buffered));
                }
            }
            var page = memory.pendingJobs(recoveryCursor, 20);
            recoveryCursor = page.nextCursor();
            page.turns().forEach(this::enqueue);
        } catch (RuntimeException unavailable) {
            log.warn("Recuperación de trabajos IA temporalmente no disponible");
        }
    }

    private void enqueue(Turn turn) {
        try { tasks.schedule(() -> runJob(turn)); }
        catch (RuntimeException busy) {
            if (!memory.durableJobs()) {
                if (memory.finish(turn.fail("AI_QUEUE_BUSY",clock.instant()))) announce(turn.fail("AI_QUEUE_BUSY",clock.instant()));
            }
        }
    }

    private void runJob(Turn turn) {
        Job job = null;
        AutoCloseable heartbeat = null;
        var valid = new java.util.concurrent.atomic.AtomicBoolean(true);
        try {
            if (memory.find(turn.projectId(),turn.requestId()).map(t -> t.state()!=State.PENDING).orElse(true)) {
                resultsAwaitingPersistence.remove(resultKey(turn));
                return;
            }
            job = memory.claim(turn, worker + "-" + UUID.randomUUID(), clock.instant().getEpochSecond(),
                    clock.instant().plusSeconds(LEASE_SECONDS).getEpochSecond()).orElse(null);
            if (job == null) return;
            Job claimed = job;
            heartbeat = tasks.heartbeat(() -> {
                try {
                    if (!memory.renew(claimed,clock.instant().getEpochSecond(),
                            clock.instant().plusSeconds(LEASE_SECONDS).getEpochSecond())) valid.set(false);
                } catch (RuntimeException unavailable) { valid.set(false); }
            });
            process(job, valid);
        } catch (RuntimeException unavailable) {
            log.warn("Trabajo IA pendiente de recuperación durable");
        } finally {
            if (heartbeat != null) try { heartbeat.close(); } catch (Exception ignored) {}
            if (job != null) try { memory.release(job); } catch (RuntimeException unavailable) {
                log.warn("La reserva IA se recuperará al vencer su lease");
            }
        }
    }

    private boolean owns(Job job, java.util.concurrent.atomic.AtomicBoolean valid) {
        return valid.get() && memory.renew(job,clock.instant().getEpochSecond(),
                clock.instant().plusSeconds(LEASE_SECONDS).getEpochSecond());
    }

    private void process(Job job, java.util.concurrent.atomic.AtomicBoolean valid) {
        Turn turn = job.turn();
        try {
            if(memory.find(turn.projectId(),turn.requestId()).map(v->v.state()!=State.PENDING).orElse(true))return;
            if(!memory.durableJobs() && !turn.createdAt().plusSeconds(180).isAfter(clock.instant())){expire(turn);return;}
            access(turn.projectId(),turn.authorId());
            if(turn.mode()==Mode.MODEL){
                projects.requireAccess(turn.projectId(),turn.authorId(),com.arqnova.modelo.entidades.ProjectMemberRole.OWNER,com.arqnova.modelo.entidades.ProjectMemberRole.EDITOR);
                var applied=mutations.find(turn);if(applied.isPresent()){finish(job,appliedTurn(turn,applied.get()));return;}
            }
            if(users.findByUsername(turn.authorId()).filter(v->!Boolean.FALSE.equals(v.getHabilitado())).isEmpty())throw new UnauthorizedActionException("Usuario deshabilitado");
            if (job.result() != null) { finish(job,job.result()); return; }
            var buffered = resultsAwaitingPersistence.get(resultKey(turn));
            if (buffered != null) { finish(job,buffered); return; }
            if (job.attempt() > MAX_ATTEMPTS) {
                finish(job,turn.fail("AI_RECOVERY_EXHAUSTED",clock.instant()));
                return;
            }
            if (!owns(job,valid)) return;
            if (turn.mode() == Mode.MODEL && !explicitModeling(turn.instruction())) {
                finish(job,turn.finish(new Answer(
                        "Confirma qué operación UML deseas aplicar e indica el elemento y los datos concretos. "
                        + "Para evaluar opciones, usa Consulta, Asesoramiento, Revisión o Mediación.",
                        List.of(), "LOCAL", "modeling-clarification"), clock.instant()));
                return;
            }
            if (turn.mode() != Mode.MODEL && explicitModeling(turn.instruction())) {
                boolean allowed = canModel(turn.projectId(), turn.authorId());
                String message = allowed
                        ? "Esta orden no cambió el lienzo. Usa Modificar diagrama guardado para aplicar la instrucción."
                        : "Esta orden no cambió el lienzo. Solo el propietario o un editor puede aplicarla.";
                finish(job,turn.finish(new Answer(message, List.of(), "LOCAL",
                        allowed ? "modeling-mode-required" : "modeling-permission-required"), clock.instant()));
                return;
            }
            var project=projects.findProjectById(turn.projectId()).orElseThrow();
            var diagram=projects.findDiagramByProjectId(turn.projectId());
            var context = contextHistory.context(turn, project.getName(),
                    diagram.map(v -> v.getModel()).orElse(Map.of("classes", List.of(), "relations", List.of())),
                    diagram.map(v -> v.getModelVersion()).orElse(0));
            Answer answer=provider.answer(turn,context);
            if(answer==null||answer.text()==null||answer.text().isBlank()||answer.text().length()>12000||answer.findings().size()>30)
                throw new IllegalArgumentException("Respuesta IA inválida");
            if(turn.mode()!=Mode.MODEL&&!answer.operations().isEmpty())throw new IllegalArgumentException("Una consulta no puede modificar el modelo");
            if(turn.mode()==Mode.MODEL&&!answer.operations().isEmpty()){
                synchronized(guard(turn)){
                    var current=memory.find(turn.projectId(),turn.requestId()).orElseThrow();
                    if(expire(current).state()!=State.PENDING || !owns(job,valid))return;
                    if(users.findByUsername(turn.authorId()).filter(v->!Boolean.FALSE.equals(v.getHabilitado())).isEmpty())throw new UnauthorizedActionException("Usuario deshabilitado");
                    finish(job,appliedTurn(turn,mutations.apply(turn,context,answer)));
                }return;
            }
            // Record the model version actually reviewed, which may have advanced while the job waited.
            var reviewed=new Turn(turn.requestId(),turn.projectId(),turn.authorId(),turn.authorName(),turn.mode(),turn.instruction(),turn.state(),null,null,
                    context.diagramVersion(),turn.createdAt(),turn.updatedAt());
            finish(job,reviewed.finish(answer,clock.instant()));
        } catch(Exception error) {
            if (error instanceof AiMemoryUnavailableException) throw (AiMemoryUnavailableException) error;
            if(turn.mode()==Mode.MODEL){var applied=mutations.find(turn);if(applied.isPresent()){finish(job,appliedTurn(turn,applied.get()));return;}}
            if (memory.durableJobs() && (Thread.currentThread().isInterrupted()
                    || error instanceof java.io.IOException || error instanceof InterruptedException
                    || (error instanceof AiProviderException provider && provider.httpStatus >= 500))) {
                log.warn("Proveedor IA interrumpido; trabajo pendiente de reintento acotado");
                return;
            }
            String code=error instanceof AiProviderException p&&p.httpStatus==429?"AI_RATE_LIMITED":error instanceof UnauthorizedActionException?"ACCESS_CHANGED":
                    error instanceof DuplicateResourceException?"AI_MODEL_CONFLICT":
                    error instanceof IllegalArgumentException&&"AI_CONTEXT_TOO_LARGE".equals(error.getMessage())?"AI_CONTEXT_TOO_LARGE":
                    turn.mode()==Mode.MODEL&&error instanceof IllegalArgumentException?"AI_INVALID_OPERATIONS":"AI_PROVIDER_UNAVAILABLE";
            finish(job,turn.fail(code,clock.instant()));
        }
    }
    private boolean explicitModeling(String instruction) {
        return !UmlModelingIntent.needsClarification(instruction) && !UNCERTAIN_MODELING.matcher(instruction).find()
                && (POLITE_MODELING.matcher(instruction).matches()
                    || (instruction.indexOf('?') < 0 && instruction.indexOf('¿') < 0
                        && DIRECT_MODELING.matcher(instruction).find()));
    }
    private Turn expire(Turn turn) {
        synchronized(guard(turn)){return expireLocked(turn);}
    }
    private Object guard(Turn turn){return mutationGuards[Math.floorMod(Objects.hash(turn.projectId(),turn.requestId()),mutationGuards.length)];}
    private Turn expireLocked(Turn turn) {
        if(turn.state()==State.PENDING&&turn.mode()==Mode.MODEL&&mutations!=null){
            var applied=mutations.find(turn);if(applied.isPresent()){
                Turn recovered=appliedTurn(turn,applied.get());if(memory.finish(recovered)){
                    resultsAwaitingPersistence.remove(resultKey(recovered));announce(recovered);
                }
                return memory.find(turn.projectId(),turn.requestId()).orElse(recovered);
            }
        }
        if (memory.durableJobs() && turn.state() == State.PENDING) {
            memory.ensurePending(turn);
            return turn;
        }
        if(turn.state()!=State.PENDING||turn.createdAt().plusSeconds(180).isAfter(clock.instant()))return turn;
        Turn expired=turn.fail("AI_REQUEST_INTERRUPTED",clock.instant());
        if(memory.finish(expired)){announce(expired);return expired;}
        return memory.find(turn.projectId(),turn.requestId()).orElse(turn);
    }
    private void finish(Job job, Turn result) {
        resultsAwaitingPersistence.put(resultKey(result),result);
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                if (!memory.stage(job,result,clock.instant().getEpochSecond())) return;
                if (memory.complete(job,result,clock.instant().getEpochSecond())) {
                    resultsAwaitingPersistence.remove(resultKey(result));
                    announce(result);
                } else if (memory.find(result.projectId(),result.requestId()).map(t -> t.state()!=State.PENDING).orElse(false)) {
                    resultsAwaitingPersistence.remove(resultKey(result));
                }
                return;
            } catch (AiMemoryUnavailableException temporary) {
                if (attempt == 2) throw temporary;
                try { Thread.sleep(100L * (attempt + 1)); }
                catch (InterruptedException stopped) { Thread.currentThread().interrupt(); throw temporary; }
            }
        }
    }
    private Turn appliedTurn(Turn turn,AiModelMutationPort.Applied applied){return new Turn(turn.requestId(),turn.projectId(),turn.authorId(),turn.authorName(),turn.mode(),turn.instruction(),State.COMPLETED,applied.answer(),null,applied.diagramVersion(),turn.createdAt(),clock.instant());}
    private void announce(Turn turn) {
        try { events.changed(new Change(turn.projectId(),turn.requestId(),turn.state(),turn.mode(),turn.diagramVersion())); }
        catch(RuntimeException unavailable) { /* Durable history is authoritative; clients can recover on reconnect. */ }
    }
    private void access(String project,String user) { projects.requireAccess(project,user); }
    private void available() { if(!memory.enabled())throw new AiMemoryUnavailableException(); }
}
