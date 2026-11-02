package com.arqnova.servicios;

import com.arqnova.modelo.Usuario;
import com.arqnova.modelo.entidades.*;
import com.arqnova.modelo.ia.AiConversation.*;
import com.arqnova.modelo.excepciones.*;
import com.arqnova.servicios.impl.AiConversationService;
import com.arqnova.servicios.puertos.entrada.*;
import com.arqnova.servicios.puertos.salida.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiConversationServiceTest {
    ProjectUseCase projects=mock(ProjectUseCase.class);
    UserQueryUseCase users=mock(UserQueryUseCase.class);
    AiConversationProviderPort provider=mock(AiConversationProviderPort.class);
    List<Runnable> queue=new ArrayList<>(); List<Change> events=new ArrayList<>(); Memory memory=new Memory();
    Clock clock=Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"),ZoneOffset.UTC);
    AiConversationService service;
    @BeforeEach void setup()throws Exception{
        when(users.findByUsername(anyString())).thenAnswer(a->Optional.of(Usuario.builder().username(a.getArgument(0)).nombreCompleto(a.getArgument(0)).habilitado(true).build()));
        when(projects.findProjectById("p")).thenReturn(Optional.of(ProjectEntity.builder().id("p").name("Biblioteca").build()));
        when(projects.findDiagramByProjectId("p")).thenReturn(Optional.of(UmlDiagramEntity.builder().id("d").projectId("p").modelVersion(5).model(Map.of("classes",List.of())).build()));
        when(provider.answer(any(),any())).thenReturn(new Answer("Respuesta breve",List.of(),"FAKE","test"));
        service=new AiConversationService(memory,provider,events::add,queue::add,projects,users,clock);
    }
    @Test void onlyRunsProviderInScheduledTask()throws Exception{
        var turn=service.send("p","a","request-001",Mode.ASK,"  Explica UML  ");
        assertThat(turn.state()).isEqualTo(State.PENDING);assertThat(turn.instruction()).isEqualTo("Explica UML");verifyNoInteractions(provider);
        queue.get(0).run();assertThat(service.get("p","a","request-001").state()).isEqualTo(State.COMPLETED);
        verify(provider,times(1)).answer(any(),any());assertThat(events).hasSize(2);
    }
    @Test void repeatedRequestDoesNotCreateAnotherProviderCall()throws Exception{
        service.send("p","a","request-001",Mode.ASK,"Explica UML");service.send("p","a","request-001",Mode.ASK,"Explica UML");
        assertThat(queue).hasSize(1);queue.get(0).run();service.send("p","a","request-001",Mode.ASK,"Explica UML");
        verify(provider,times(1)).answer(any(),any());assertThat(memory.data).hasSize(1);
    }
    @Test void rejectsReusingRequestForAnotherUserOrInstruction(){
        service.send("p","a","request-001",Mode.ASK,"Explica UML");
        assertThatThrownBy(()->service.send("p","b","request-001",Mode.ASK,"Explica UML")).isInstanceOf(DuplicateResourceException.class);
        assertThatThrownBy(()->service.send("p","a","request-001",Mode.ASK,"Otra pregunta")).isInstanceOf(DuplicateResourceException.class);
        assertThat(queue).hasSize(1);
    }
    @Test void loadsGroupAndIndividualHistoryWithoutModifyingDiagram()throws Exception{
        service.send("p","a","request-001",Mode.ASK,"Primera");queue.remove(0).run();
        service.send("p","b","request-002",Mode.REVIEW,"Revisa el modelo");queue.remove(0).run();
        var capture=org.mockito.ArgumentCaptor.forClass(Context.class);verify(provider,times(2)).answer(any(),capture.capture());
        var context=capture.getAllValues().get(1);assertThat(context.groupHistory()).hasSize(1);assertThat(context.userHistory()).isEmpty();
        assertThat(context.diagramVersion()).isEqualTo(5);verify(projects,never()).saveDiagram(anyString(),anyString(),anyMap(),any());
    }
    @Test void rejectsAccessBeforeReadingHistory(){
        when(projects.requireAccess(eq("p"),eq("intruder"))).thenThrow(new UnauthorizedActionException("Sin acceso"));
        assertThatThrownBy(()->service.history("p","intruder",null,20)).isInstanceOf(UnauthorizedActionException.class);
    }
    @Test void storesFailureAndDoesNotRepeatOnRetry()throws Exception{
        when(provider.answer(any(),any())).thenThrow(new AiProviderException(429));
        service.send("p","a","request-001",Mode.ASK,"Explica");queue.get(0).run();
        var turn=service.get("p","a","request-001");assertThat(turn.errorCode()).isEqualTo("AI_RATE_LIMITED");
        service.send("p","a","request-001",Mode.ASK,"Explica");verify(provider,times(1)).answer(any(),any());
    }
    @Test void marksInterruptedRequestsAndDoesNotBillLateQueuedTask()throws Exception{
        service.send("p","a","request-001",Mode.ASK,"Explica");
        service=new AiConversationService(memory,provider,events::add,queue::add,projects,users,Clock.offset(clock,Duration.ofMinutes(4)));
        assertThat(service.get("p","a","request-001").errorCode()).isEqualTo("AI_REQUEST_INTERRUPTED");
        queue.get(0).run();verifyNoInteractions(provider);
    }
    @Test void invalidInputNeverCreatesJobs(){
        assertThatThrownBy(()->service.send("p","a","bad",Mode.ASK,"Hola")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.send("p","a","request-001",Mode.ASK,"x".repeat(4001))).isInstanceOf(IllegalArgumentException.class);
        assertThat(queue).isEmpty();assertThat(memory.data).isEmpty();
    }
    @Test void unavailableMemoryDoesNotFallBackToVolatileHistory(){
        memory.enabled=false;
        assertThatThrownBy(()->service.send("p","a","request-001",Mode.ASK,"Hola")).isInstanceOf(AiMemoryUnavailableException.class);
        assertThat(queue).isEmpty();
    }
    @Test void disabledUserCannotSend(){
        when(users.findByUsername("disabled")).thenReturn(Optional.of(Usuario.builder().habilitado(false).build()));
        assertThatThrownBy(()->service.send("p","disabled","request-001",Mode.ASK,"Hola")).isInstanceOf(UnauthorizedActionException.class);
    }
    @Test void queueRejectionBecomesRecoverableFailure(){
        service=new AiConversationService(memory,provider,events::add,task->{throw new java.util.concurrent.RejectedExecutionException();},projects,users,clock);
        service.send("p","a","request-001",Mode.ASK,"Hola");
        assertThat(service.get("p","a","request-001").errorCode()).isEqualTo("AI_QUEUE_BUSY");
    }
    @Test void modelingAppliesOnceAndPublishesResultingVersion()throws Exception{
        var mutations=mock(AiModelMutationPort.class);when(mutations.enabled()).thenReturn(true);
        var op=com.arqnova.modelo.dto.response.UmlAiOperationResponse.builder().type("CLASS_CREATE").parameters(Map.of("name","Cliente")).build();
        var answer=new Answer("Cambio aplicado",List.of(),"FAKE","test",List.of(op));when(provider.answer(any(),any())).thenReturn(answer);
        when(mutations.apply(any(),any(),any())).thenReturn(new AiModelMutationPort.Applied(answer,6));
        service=new AiConversationService(memory,provider,events::add,queue::add,projects,users,clock,mutations);
        service.send("p","a","model-001",Mode.MODEL,"Crea Cliente");queue.get(0).run();
        service.send("p","a","model-001",Mode.MODEL,"Crea Cliente");
        assertThat(service.get("p","a","model-001").diagramVersion()).isEqualTo(6);
        verify(mutations,times(1)).apply(any(),any(),any());verify(provider,times(1)).answer(any(),any());
        assertThat(events.get(events.size()-1).mode()).isEqualTo(Mode.MODEL);
    }
    @Test void modelConflictIsReportedWithoutApplyingAgain()throws Exception{
        var mutations=mock(AiModelMutationPort.class);when(mutations.enabled()).thenReturn(true);
        var answer=new Answer("Cambio",List.of(),"FAKE","test",List.of(com.arqnova.modelo.dto.response.UmlAiOperationResponse.builder().type("CLASS_CREATE").parameters(Map.of("name","Cliente")).build()));
        when(provider.answer(any(),any())).thenReturn(answer);when(mutations.apply(any(),any(),any())).thenThrow(new DuplicateResourceException("Versión diferente"));
        service=new AiConversationService(memory,provider,events::add,queue::add,projects,users,clock,mutations);
        service.send("p","a","model-002",Mode.MODEL,"Crea Cliente");queue.get(0).run();
        assertThat(service.get("p","a","model-002").errorCode()).isEqualTo("AI_MODEL_CONFLICT");
    }
    @Test void modelingRemainsDisabledUnlessExplicitlyConfigured(){
        assertThatThrownBy(()->service.send("p","a","model-003",Mode.MODEL,"Crea Cliente")).isInstanceOf(IllegalArgumentException.class);
        assertThat(memory.data).isEmpty();assertThat(queue).isEmpty();
    }
    @Test void clarificationCompletesWithoutWritingDiagram()throws Exception{
        var mutations=mock(AiModelMutationPort.class);when(mutations.enabled()).thenReturn(true);
        service=new AiConversationService(memory,provider,events::add,queue::add,projects,users,clock,mutations);
        service.send("p","a","model-004",Mode.MODEL,"Crea algo");queue.get(0).run();
        verify(mutations,never()).apply(any(),any(),any());assertThat(service.get("p","a","model-004").state()).isEqualTo(State.COMPLETED);
    }
    static class Memory implements AiMemoryPort{
        boolean enabled=true;Map<String,Turn> data=new LinkedHashMap<>();
        String key(String p,String r){return p+":"+r;}
        public boolean enabled(){return enabled;}
        public Submission begin(Turn turn){String key=key(turn.projectId(),turn.requestId());Turn old=data.putIfAbsent(key,turn);return new Submission(old==null?turn:old,old==null);}
        public Optional<Turn> find(String p,String r){return Optional.ofNullable(data.get(key(p,r)));}
        public Page history(String p,String c,int l){var list=new ArrayList<>(data.values().stream().filter(v->v.projectId().equals(p)).toList());Collections.reverse(list);return new Page(list.stream().limit(l).toList(),null);}
        public List<Turn> recentByUser(String p,String u,int l){return history(p,null,100).turns().stream().filter(v->v.authorId().equals(u)).limit(l).toList();}
        public boolean finish(Turn turn){String k=key(turn.projectId(),turn.requestId());if(data.get(k).state()!=State.PENDING)return false;data.put(k,turn);return true;}
    }
}
