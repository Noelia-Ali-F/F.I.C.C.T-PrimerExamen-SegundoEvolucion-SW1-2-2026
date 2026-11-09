package com.arqnova.controladores;

import com.arqnova.modelo.entidades.UmlDiagramEntity;
import com.arqnova.modelo.excepciones.DuplicateResourceException;
import com.arqnova.modelo.excepciones.UnauthorizedActionException;
import com.arqnova.servicios.puertos.entrada.ProjectUseCase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UmlProjectRealtimeTest {
    @Test void notifiesOnlyAfterAuthorizedSaveReturnsWithoutCreatingAiTurn() {
        var service=mock(ProjectUseCase.class);
        var messaging=mock(SimpMessagingTemplate.class);
        var model=Map.<String,Object>of("classes",java.util.List.of());
        var saved=UmlDiagramEntity.builder().projectId("p").modelVersion(8).model(model).build();
        when(service.saveDiagramFromRest("diagram-p","p",model,7,"ana")).thenReturn(saved);
        new UmlProjectController(service,messaging).saveDiagram("p",()->"ana",7,model);
        var order=inOrder(service,messaging);
        order.verify(service).saveDiagramFromRest("diagram-p","p",model,7,"ana");
        order.verify(messaging).convertAndSend("/topic/ai/p",Map.of("projectId","p","state","DIAGRAM_UPDATED","diagramVersion",8));
        verifyNoMoreInteractions(messaging);
    }

    @Test void rejectedOrConflictingSaveDoesNotAnnounceChanges() {
        for(var error:java.util.List.of(new DuplicateResourceException("conflict"),new UnauthorizedActionException("denied"))) {
            var service=mock(ProjectUseCase.class);
            var messaging=mock(SimpMessagingTemplate.class);
            var model=Map.<String,Object>of();
            when(service.saveDiagramFromRest("diagram-p","p",model,7,"ana")).thenThrow(error);
            assertThatThrownBy(()->new UmlProjectController(service,messaging).saveDiagram("p",()->"ana",7,model)).isSameAs(error);
            verifyNoInteractions(messaging);
        }
    }

    @Test void lostNotificationDoesNotReportCommittedSaveAsFailed() {
        var service=mock(ProjectUseCase.class);
        var messaging=mock(SimpMessagingTemplate.class);
        var model=Map.<String,Object>of();
        when(service.saveDiagramFromRest("diagram-p","p",model,7,"ana"))
                .thenReturn(UmlDiagramEntity.builder().projectId("p").modelVersion(8).model(model).build());
        doThrow(new IllegalStateException("broker unavailable")).when(messaging).convertAndSend(anyString(),any(Object.class));
        assertThatCode(()->new UmlProjectController(service,messaging).saveDiagram("p",()->"ana",7,model)).doesNotThrowAnyException();
    }
}
