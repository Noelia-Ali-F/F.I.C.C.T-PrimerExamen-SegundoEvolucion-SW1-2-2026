package com.arqnova.servicios.impl;

import com.arqnova.modelo.entidades.UmlCollaborationActivityEntity;
import com.arqnova.modelo.ia.AiConversation.*;
import com.arqnova.servicios.puertos.salida.AiMemoryPort;
import com.arqnova.servicios.puertos.salida.UmlCollaborationActivityDataPort;

import java.text.Normalizer;
import java.time.ZoneId;
import java.util.*;

final class AiContextHistory {
    private static final Set<String> COMMON = Set.of("para", "como", "cual", "cuales", "sobre", "esta", "este",
            "esto", "esas", "esos", "quien", "quienes", "quiero", "dime", "segun", "entre", "cada", "cuando", "donde",
            "diagrama", "proyecto", "anterior", "crea", "cambia", "modifica", "agrega", "revisa");
    private final AiMemoryPort memory;
    private final UmlCollaborationActivityDataPort activities;

    AiContextHistory(AiMemoryPort memory, UmlCollaborationActivityDataPort activities) {
        this.memory = memory;
        this.activities = activities;
    }

    Context context(Turn turn, String projectName, Map<String,Object> diagram, int version) {
        var first = memory.history(turn.projectId(), null, 30);
        var group = first.turns().stream().filter(other -> visible(turn, other)).limit(6).toList();
        var individual = memory.recentByUser(turn.projectId(), turn.authorId(), 20).stream()
                .filter(other -> visible(turn, other) && other.state() == State.COMPLETED
                        && turn.authorId().equals(other.authorId())).limit(4).toList();

        var terms = words(turn.instruction());
        var seen = new HashSet<String>();
        group.forEach(other -> seen.add(other.requestId()));
        individual.forEach(other -> seen.add(other.requestId()));
        var candidates = new ArrayList<>(first.turns());
        String cursor = first.nextCursor();
        for (int i = 0; !terms.isEmpty() && cursor != null && i < 2; i++) {
            var page = memory.history(turn.projectId(), cursor, 30);
            candidates.addAll(page.turns());
            cursor = page.nextCursor();
        }
        var relevant = candidates.stream().filter(other -> visible(turn, other) && seen.add(other.requestId()))
                .map(other -> Map.entry(other, score(terms, other.instruction() + " "
                        + (other.answer() == null ? "" : other.answer().text()) + " " + other.authorName())))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<Turn,Integer>comparingByValue().reversed())
                .limit(3).map(Map.Entry::getKey).toList();

        var changes = recentChanges(turn, diagram, terms);
        return new Context(turn.projectId(), projectName, turn.authorId(), diagram, version, group, individual, relevant, changes);
    }

    private List<Activity> recentChanges(Turn turn, Map<String,Object> diagram, Set<String> terms) {
        if (activities == null) return List.of();
        var names = new HashMap<String,String>();
        collectNames(diagram, names);
        return activities.projectHistory(turn.projectId(), null, null, 60).stream()
                .filter(row -> turn.projectId().equals(row.getProjectId()) && row.getOccurredAt() != null)
                .map(row -> {
                    String name = names.get(row.getElementId());
                    return Map.entry(toActivity(row, name), 2 * score(terms, row.getOperation() + " "
                            + name + " " + row.getElementId() + " " + row.getUserName())
                            + (turn.authorId().equals(row.getUserId()) ? 1 : 0));
                })
                .sorted(Map.Entry.<Activity,Integer>comparingByValue().reversed())
                .limit(5).map(Map.Entry::getKey).toList();
    }

    private Activity toActivity(UmlCollaborationActivityEntity row, String name) {
        return new Activity(row.getUserId(), row.getUserName(), row.getOccurredAt().atZone(ZoneId.systemDefault()).toInstant(),
                row.getElementId(), name, row.getElementType(), row.getEventType(), row.getOperation());
    }

    private boolean visible(Turn current, Turn other) {
        return current.projectId().equals(other.projectId()) && !current.requestId().equals(other.requestId())
                && (other.state() == State.COMPLETED || (current.mode() == Mode.MEDIATE && other.state() == State.PENDING));
    }

    private static Set<String> words(String text) {
        String normal = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        var words = new LinkedHashSet<String>();
        for (String word : normal.split("[^a-z0-9]+")) {
            if (word.length() >= 4 && !COMMON.contains(word)) words.add(word);
            if (words.size() == 16) break;
        }
        return words;
    }

    private int score(Set<String> terms, String text) {
        if (terms.isEmpty()) return 0;
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        var tokens = new HashSet<>(Arrays.asList(normalized.split("[^a-z0-9]+")));
        return (int) terms.stream().filter(tokens::contains).count();
    }

    private void collectNames(Object value, Map<String,String> names) {
        if (value instanceof Map<?,?> row) {
            if (row.get("id") instanceof String id && row.get("name") instanceof String name) names.put(id, name);
            row.values().forEach(child -> collectNames(child, names));
        } else if (value instanceof List<?> rows) {
            rows.forEach(child -> collectNames(child, names));
        }
    }
}
