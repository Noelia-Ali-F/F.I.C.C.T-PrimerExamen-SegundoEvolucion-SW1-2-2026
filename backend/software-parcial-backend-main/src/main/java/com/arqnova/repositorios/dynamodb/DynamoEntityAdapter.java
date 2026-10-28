package com.arqnova.repositorios.dynamodb;

import com.arqnova.servicios.puertos.salida.EntityDataPort;
import java.util.*;
import java.util.function.Function;

abstract class DynamoEntityAdapter<T, K> implements EntityDataPort<T, K> {
    protected final DynamoDocumentStore store;
    protected final String kind;
    protected final Class<T> type;
    private final Function<T, K> identity;
    private final Function<K, String> key;
    DynamoEntityAdapter(DynamoDocumentStore store, String kind, Class<T> type, Function<T, K> identity, Function<K, String> key) {
        this.store = store; this.kind = kind; this.type = type; this.identity = identity; this.key = key;
    }
    public Optional<T> findById(K id) { return store.get(kind, key.apply(id), type); }
    public boolean existsById(K id) { return findById(id).isPresent(); }
    public T save(T value) {
        return store.atomic(() -> {
            String id = key.apply(identity.apply(value));
            T old = store.get(kind, id, type).orElse(null);
            prepare(value, old); return store.put(kind, id, value);
        });
    }
    protected void prepare(T value, T previous) {}
    protected void deleting(T value) {}
    public void deleteById(K id) {
        store.atomic(() -> { findById(id).ifPresent(this::deleting); store.delete(kind, key.apply(id)); return null; });
    }
    public List<T> findAll() { return store.list(kind, type); }
    public long count() { return findAll().size(); }
    public void deleteAll() { findAll().forEach(v -> deleteById(identity.apply(v))); }
    public void flush() { /* Commit belongs to the outer unit of work, not an individual repository. */ }
    static String compound(String... values) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(String.join("\u0000", values).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
