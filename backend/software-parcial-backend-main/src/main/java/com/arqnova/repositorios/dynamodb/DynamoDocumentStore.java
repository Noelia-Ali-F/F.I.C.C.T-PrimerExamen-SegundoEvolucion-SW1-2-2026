package com.arqnova.repositorios.dynamodb;

import com.arqnova.modelo.excepciones.DuplicateResourceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.io.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.zip.*;

/** DynamoDB unit of work. All mutations are staged until a single conditional transaction commits. */
public final class DynamoDocumentStore {
    public static final int CHUNK_BYTES = 280 * 1024;
    private static final int MAX_TRANSACTION_BYTES = 3_500_000;
    private final DynamoDbClient client;
    private final String table;
    private final ObjectMapper json;
    private final ThreadLocal<Unit> current = new ThreadLocal<>();
    record Key(String kind, String id) {}
    record Snapshot(byte[] data, String revision, int chunks, int storedBytes) {}
    static final class Unit {
        final Map<Key, Snapshot> read = new HashMap<>();
        final Map<Key, byte[]> writes = new LinkedHashMap<>();
        boolean rollbackOnly;
    }
    public DynamoDocumentStore(DynamoDbClient client, String table, ObjectMapper json) {
        this.client = client; this.table = table; this.json = json;
    }
    Unit unit() { return current.get(); }
    void begin() { if (current.get() != null) throw new IllegalStateException("Transacción ya iniciada"); current.set(new Unit()); }
    void end() { current.remove(); }
    public <T> T atomic(Supplier<T> action) {
        if (unit() != null) {
            try { return action.get(); }
            catch (RuntimeException e) { unit().rollbackOnly = true; throw e; }
        }
        begin();
        try { T result = action.get(); commit(); return result; } finally { end(); }
    }
    public <T> Optional<T> get(String kind, String id, Class<T> type) {
        Key key = new Key(kind, id);
        byte[] bytes = unit() != null && unit().writes.containsKey(key) ? unit().writes.get(key) : snapshot(key).data();
        if (bytes == null) return Optional.empty();
        try { return Optional.of(json.readValue(bytes, type)); }
        catch (IOException e) { throw new IllegalStateException("Documento DynamoDB inválido", e); }
    }
    public <T> T put(String kind, String id, T value) {
        return atomic(() -> {
            Key key = new Key(kind, id); snapshot(key);
            try {
                byte[] data = json.writeValueAsBytes(value);
                if (data.length > 32 * 1024 * 1024) throw new IllegalArgumentException("Documento demasiado grande; no se guardó ningún cambio");
                unit().writes.put(key, data); return value;
            }
            catch (IOException e) { throw new IllegalArgumentException("Documento no serializable", e); }
        });
    }
    public void delete(String kind, String id) {
        atomic(() -> { Key key = new Key(kind, id); snapshot(key); unit().writes.put(key, null); return null; });
    }
    /** Touch a parent in the same transaction: concurrent insertion/deletion cannot leave orphans. */
    public void requireParent(String kind, String id) {
        if (unit() == null) throw new IllegalStateException("Se requiere una transacción");
        Key key = new Key(kind, id);
        byte[] bytes = unit().writes.containsKey(key) ? unit().writes.get(key) : snapshot(key).data();
        if (bytes == null) throw new IllegalArgumentException("El recurso asociado no existe: " + kind);
        unit().writes.put(key, bytes);
    }
    /** A unique value is a conditional item, never a check followed by an unconditional write. */
    public void reserve(String namespace, String value, String owner) {
        String key = uniqueKey(namespace, value);
        var existing = get("UNIQUE", key, Unique.class);
        if (existing.isPresent() && !existing.get().owner().equals(owner))
            throw new DuplicateResourceException("Ya existe un registro con ese nombre o identificador");
        put("UNIQUE", key, new Unique(owner));
    }
    public void release(String namespace, String value, String owner) {
        String key = uniqueKey(namespace, value);
        if (get("UNIQUE", key, Unique.class).filter(v -> v.owner().equals(owner)).isPresent()) delete("UNIQUE", key);
    }
    public record Unique(String owner) {}
    static String uniqueKey(String namespace, String value) {
        return namespace + ":" + (Set.of("username", "department-name", "invite").contains(namespace) ? value.toLowerCase(Locale.ROOT) : value);
    }
    public <T> List<T> list(String kind, Class<T> type) {
        Set<String> ids = new LinkedHashSet<>();
        Map<String, AttributeValue> next = null;
        do {
            var page = client.query(QueryRequest.builder().tableName(table).consistentRead(true)
                    .keyConditionExpression("pk = :kind").expressionAttributeValues(Map.of(":kind", text(kind)))
                    .projectionExpression("sk").exclusiveStartKey(next).build());
            page.items().forEach(v -> ids.add(v.get("sk").s())); next = page.lastEvaluatedKey();
        } while (next != null && !next.isEmpty());
        if (unit() != null) unit().writes.keySet().stream().filter(k -> k.kind().equals(kind)).forEach(k -> ids.add(k.id()));
        return ids.stream().map(id -> get(kind, id, type)).flatMap(Optional::stream).toList();
    }
    private Snapshot snapshot(Key key) {
        if (unit() != null && unit().read.containsKey(key)) return unit().read.get(key);
        var item = client.getItem(GetItemRequest.builder().tableName(table).key(key(key)).consistentRead(true).build()).item();
        Snapshot found;
        if (item.isEmpty()) found = new Snapshot(null, null, 0, 0);
        else {
            String revision = item.get("revision").s();
            int chunks = item.containsKey("chunks") ? Integer.parseInt(item.get("chunks").n()) : 0;
            try {
                var bytes = new ByteArrayOutputStream();
                if (chunks == 0) bytes.write(item.get("payload").b().asByteArray());
                else for (int i = 0; i < chunks; i++) {
                    var part = client.getItem(GetItemRequest.builder().tableName(table)
                            .key(blobKey(key, revision, i)).consistentRead(true).build()).item();
                    if (part.isEmpty()) throw conflict();
                    bytes.write(part.get("payload").b().asByteArray());
                }
                try (var gzip = new GZIPInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                    byte[] decoded = gzip.readNBytes(32 * 1024 * 1024 + 1);
                    if (decoded.length > 32 * 1024 * 1024) throw new IllegalStateException("Documento demasiado grande");
                    found = new Snapshot(decoded, revision, chunks, bytes.size());
                }
            } catch (IOException e) { throw new IllegalStateException("No se pudo leer el documento DynamoDB", e); }
        }
        if (unit() != null) unit().read.put(key, found);
        return found;
    }
    void commit() {
        if (unit().rollbackOnly) throw new org.springframework.transaction.UnexpectedRollbackException("La operación fue revertida por un fallo previo");
        if (unit().writes.isEmpty()) return;
        List<TransactWriteItem> operations = new ArrayList<>();
        int totalBytes = 0;
        for (var entry : unit().writes.entrySet()) {
            Key k = entry.getKey(); Snapshot old = unit().read.get(k);
            totalBytes += old.storedBytes();
            String revision = UUID.randomUUID().toString();
            String condition = old.revision() == null ? "attribute_not_exists(pk)" : "revision = :previous";
            Map<String, AttributeValue> expected = old.revision() == null ? null : Map.of(":previous", text(old.revision()));
            if (entry.getValue() == null) {
                operations.add(TransactWriteItem.builder().delete(Delete.builder().tableName(table).key(key(k))
                        .conditionExpression(condition).expressionAttributeValues(expected).build()).build());
            } else {
                byte[] compressed;
                try {
                    var buffer = new ByteArrayOutputStream();
                    try (var gzip = new GZIPOutputStream(buffer)) { gzip.write(entry.getValue()); }
                    compressed = buffer.toByteArray();
                } catch (IOException e) { throw new IllegalStateException(e); }
                totalBytes += compressed.length;
                Map<String, AttributeValue> item = new HashMap<>(key(k)); item.put("revision", text(revision));
                if (compressed.length <= CHUNK_BYTES) item.put("payload", binary(compressed));
                else {
                    int count = (compressed.length + CHUNK_BYTES - 1) / CHUNK_BYTES;
                    item.put("chunks", AttributeValue.builder().n(String.valueOf(count)).build());
                    for (int i = 0; i < count; i++) {
                        Map<String, AttributeValue> part = new HashMap<>(blobKey(k, revision, i));
                        part.put("payload", binary(Arrays.copyOfRange(compressed, i * CHUNK_BYTES, Math.min(compressed.length, (i + 1) * CHUNK_BYTES))));
                        operations.add(TransactWriteItem.builder().put(Put.builder().tableName(table).item(part).build()).build());
                    }
                }
                operations.add(TransactWriteItem.builder().put(Put.builder().tableName(table).item(item)
                        .conditionExpression(condition).expressionAttributeValues(expected).build()).build());
            }
            for (int i = 0; i < old.chunks(); i++) operations.add(TransactWriteItem.builder()
                    .delete(Delete.builder().tableName(table).key(blobKey(k, old.revision(), i)).build()).build());
        }
        // Fail before any write rather than split a business transaction into partially committed batches.
        if (operations.size() > 100 || totalBytes > MAX_TRANSACTION_BYTES)
            throw new IllegalArgumentException("La operación supera el tamaño de una transacción DynamoDB. No se guardó ningún cambio.");
        try { client.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(operations)
                .clientRequestToken(UUID.randomUUID().toString()).build()); }
        catch (TransactionCanceledException e) {
            if (e.cancellationReasons().stream().anyMatch(r -> "ConditionalCheckFailed".equals(r.code()) || "TransactionConflict".equals(r.code()))) throw conflict();
            throw e;
        }
    }
    private static DuplicateResourceException conflict() {
        return new DuplicateResourceException("Los datos fueron actualizados por otra sesión. Recarga antes de guardar");
    }
    private static AttributeValue text(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue binary(byte[] value) { return AttributeValue.builder().b(SdkBytes.fromByteArray(value)).build(); }
    private static Map<String, AttributeValue> key(Key k) { return Map.of("pk", text(k.kind()), "sk", text(k.id())); }
    private static Map<String, AttributeValue> blobKey(Key k, String rev, int index) {
        return Map.of("pk", text("BLOB:" + k.kind() + ":" + k.id()), "sk", text(rev + ":" + index));
    }
}
