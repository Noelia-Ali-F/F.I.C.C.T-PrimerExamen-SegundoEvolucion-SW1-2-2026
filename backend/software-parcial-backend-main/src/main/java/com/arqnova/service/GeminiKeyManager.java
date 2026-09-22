package com.arqnova.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Mantiene las claves de Gemini sin exponerlas y coordina su rotación entre solicitudes concurrentes. */
@Component
public class GeminiKeyManager {
    private final List<String> keys;
    private final AtomicInteger activeIndex = new AtomicInteger();

    public GeminiKeyManager(
            @Value("${app.gemini.api-key-1:}") String key1,
            @Value("${app.gemini.api-key-2:}") String key2,
            @Value("${app.gemini.api-key-3:}") String key3,
            @Value("${app.gemini.api-key:}") String legacyKey) {
        LinkedHashSet<String> configured = new LinkedHashSet<>();
        add(configured, key1);
        // Mientras una instalación migra desde GEMINI_API_KEY, esa clave conserva
        // la prioridad principal si GEMINI_API_KEY_1 todavía no fue configurada.
        if (key1 == null || key1.isBlank()) add(configured, legacyKey);
        add(configured, key2);
        add(configured, key3);
        add(configured, legacyKey);
        this.keys = List.copyOf(configured);
    }

    public boolean isConfigured() {
        return !keys.isEmpty();
    }

    public List<String> keysFromActive() {
        if (keys.isEmpty()) return List.of();
        int start = Math.floorMod(activeIndex.get(), keys.size());
        List<String> ordered = new ArrayList<>(keys.size());
        for (int offset = 0; offset < keys.size(); offset++) {
            ordered.add(keys.get((start + offset) % keys.size()));
        }
        return List.copyOf(ordered);
    }

    public void markQuotaExhausted(String exhaustedKey) {
        if (keys.size() < 2) return;
        int exhaustedIndex = keys.indexOf(exhaustedKey);
        if (exhaustedIndex < 0) return;
        activeIndex.compareAndSet(exhaustedIndex, (exhaustedIndex + 1) % keys.size());
    }

    int activeIndex() {
        return activeIndex.get();
    }

    private static void add(LinkedHashSet<String> values, String candidate) {
        if (candidate != null && !candidate.isBlank()) values.add(candidate.trim());
    }
}
