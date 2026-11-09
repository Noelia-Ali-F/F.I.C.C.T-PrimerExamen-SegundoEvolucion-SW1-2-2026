package com.arqnova.servicios.impl;

import com.arqnova.modelo.entidades.UmlCollaborationActivityEntity;
import com.arqnova.modelo.ia.AiConversation.*;
import com.arqnova.servicios.puertos.salida.AiMemoryPort;
import com.arqnova.servicios.puertos.salida.UmlCollaborationActivityDataPort;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AiContextHistoryTest {
    @Test void retrievesRelevantOlderTurnsAndActivitiesWithoutMixingProjectsOrPeople() {
        var memory = mock(AiMemoryPort.class);
        var log = mock(UmlCollaborationActivityDataPort.class);
        var current = turn("p", "ana1", "current", "¿Qué se decidió sobre Inventario?", State.PENDING);
        var recent = turn("p", "ana2", "recent", "Propongo separar Venta", State.COMPLETED);
        var personal = turn("p", "ana1", "personal", "Mi aporte sobre pedidos", State.COMPLETED);
        var old = turn("p", "ana2", "old", "Decisión: conservar Inventario", State.COMPLETED);
        var foreign = turn("foreign", "ana1", "foreign", "Decisión: borrar Inventario", State.COMPLETED);
        when(memory.history("p", null, 30)).thenReturn(new Page(List.of(current, recent), "next"));
        when(memory.history("p", "next", 30)).thenReturn(new Page(List.of(old, foreign), "tail"));
        when(memory.history("p", "tail", 30)).thenReturn(new Page(List.of(), "stop"));
        when(memory.recentByUser("p", "ana1", 20)).thenReturn(List.of(personal, recent, foreign));
        var event = UmlCollaborationActivityEntity.builder().projectId("p").userId("ana2").userName("Ana")
                .elementId("inventory").elementType("class").eventType("class:update").operation("Renombró una clase")
                .occurredAt(LocalDateTime.of(2026, 10, 1, 9, 0)).build();
        var foreignEvent = UmlCollaborationActivityEntity.builder().projectId("foreign").userId("ana1").userName("Ana")
                .elementId("inventory").occurredAt(event.getOccurredAt()).build();
        when(log.projectHistory("p", null, null, 60)).thenReturn(List.of(foreignEvent, event));

        var context = new AiContextHistory(memory, log).context(current, "Proyecto",
                Map.of("classes", List.of(Map.of("id", "inventory", "name", "Inventario"))), 7);
        assertThat(context.groupHistory()).containsExactly(recent);
        assertThat(context.userHistory()).containsExactly(personal);
        assertThat(context.relevantHistory()).containsExactly(old);
        assertThat(context.activities()).singleElement().satisfies(activity -> {
            assertThat(activity.authorId()).isEqualTo("ana2");
            assertThat(activity.authorName()).isEqualTo("Ana");
            assertThat(activity.elementName()).isEqualTo("Inventario");
            assertThat(activity.action()).isEqualTo("class:update");
            assertThat(activity.at()).isEqualTo(event.getOccurredAt().atZone(ZoneId.systemDefault()).toInstant());
        });
        verify(memory, never()).history("p", "stop", 30);
    }

    private Turn turn(String project, String actor, String id, String text, State state) {
        var at = Instant.parse("2026-10-01T12:00:00Z");
        return new Turn(id, project, actor, "Ana", Mode.ASK, text, state, null, null, 7, at, at);
    }
}
