package com.arqnova.servicios;
import com.arqnova.servicios.impl.ProjectActivityService;
import com.arqnova.servicios.puertos.entrada.ProjectUseCase;
import com.arqnova.servicios.puertos.salida.UmlCollaborationActivityDataPort;
import com.arqnova.modelo.entidades.UmlCollaborationActivityEntity;
import com.arqnova.modelo.excepciones.UnauthorizedActionException;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ProjectActivityServiceTest {
    final ProjectUseCase projects=mock(ProjectUseCase.class);
    final UmlCollaborationActivityDataPort data=mock(UmlCollaborationActivityDataPort.class);
    final ProjectActivityService service=new ProjectActivityService(projects,data,ZoneOffset.UTC);
    final LocalDateTime time=LocalDateTime.of(2026,11,2,10,0);
    UmlCollaborationActivityEntity row(String id){return UmlCollaborationActivityEntity.builder().eventId(id).userName("Noelia").eventType("class:create").operation("Creó Libro").occurredAt(time).build();}
    @Test void returnsAuthorAndTimestampWithCursorThatPreservesEqualTimestampOrdering(){
        when(data.projectHistory("p",null,null,3)).thenReturn(List.of(row("c"),row("b"),row("a")));
        var first=service.history("p","u",null,2);
        assertThat(first.items()).hasSize(2);assertThat(first.items().get(0).author()).isEqualTo("Noelia");
        assertThat(first.items().get(0).timestamp()).isEqualTo(time.toInstant(ZoneOffset.UTC).toEpochMilli());
        when(data.projectHistory("p",time,"b",3)).thenReturn(List.of(row("a")));
        var next=service.history("p","u",first.nextCursor(),2);
        assertThat(next.items()).extracting(com.arqnova.modelo.ia.ProjectActivity::id).containsExactly("a");assertThat(next.nextCursor()).isNull();
    }
    @Test void checksProjectMembershipBeforeReadingAnyHistory(){
        when(projects.requireAccess("p","other")).thenThrow(new UnauthorizedActionException("Sin acceso"));
        assertThatThrownBy(()->service.history("p","other",null,20)).isInstanceOf(UnauthorizedActionException.class);verifyNoInteractions(data);
    }
    @Test void rejectsInvalidAndOversizedCursors(){
        for(String cursor:List.of("%%%","a".repeat(600),Base64.getUrlEncoder().encodeToString("bad-date|id".getBytes())))
            assertThatThrownBy(()->service.history("p","u",cursor,20)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(data);
    }
    @Test void boundsThePageSize(){
        for(int limit:List.of(0,101))assertThatThrownBy(()->service.history("p","u",null,limit)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(data);
    }
}
