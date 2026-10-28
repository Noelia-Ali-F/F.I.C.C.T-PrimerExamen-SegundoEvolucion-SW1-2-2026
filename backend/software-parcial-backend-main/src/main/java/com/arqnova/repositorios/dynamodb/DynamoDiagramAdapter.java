package com.arqnova.repositorios.dynamodb;

import com.arqnova.modelo.entidades.UmlDiagramEntity;
import com.arqnova.modelo.excepciones.DuplicateResourceException;
import com.arqnova.servicios.puertos.salida.UmlDiagramDataPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;

@Repository @Profile("dynamodb")
public class DynamoDiagramAdapter extends DynamoEntityAdapter<UmlDiagramEntity, String> implements UmlDiagramDataPort {
    public DynamoDiagramAdapter(DynamoDocumentStore store) { super(store, "DIAGRAM", UmlDiagramEntity.class, UmlDiagramEntity::getId, v -> v); }
    protected void prepare(UmlDiagramEntity v, UmlDiagramEntity old) {
        store.requireParent("PROJECT", v.getProjectId());
        if (old != null && !old.getProjectId().equals(v.getProjectId())) throw new IllegalArgumentException("El proyecto del diagrama es inmutable");
        if (old != null && v.getModelVersion() != old.getModelVersion() + 1) throw new DuplicateResourceException("Versión de diagrama desactualizada");
        store.reserve("diagram-project", v.getProjectId(), v.getId());
        if (v.getModel() == null) v.setModel(new LinkedHashMap<>());
        if (v.getModelVersion() < 1) v.setModelVersion(1);
        v.setCreatedAt(old != null ? old.getCreatedAt() : v.getCreatedAt() == null ? LocalDateTime.now() : v.getCreatedAt());
        if (old != null || v.getUpdatedAt() == null) v.setUpdatedAt(LocalDateTime.now());
    }
    protected void deleting(UmlDiagramEntity v) { store.release("diagram-project", v.getProjectId(), v.getId()); }
    public Optional<UmlDiagramEntity> findByProjectId(String id) { return store.get("UNIQUE", DynamoDocumentStore.uniqueKey("diagram-project", id), DynamoDocumentStore.Unique.class).flatMap(v -> findById(v.owner())); }
    // There is no SQL lock: the unit of work retains the revision and commits with compare-and-swap.
    public Optional<UmlDiagramEntity> findByProjectIdForUpdate(String id) { return findByProjectId(id); }
    public long countByProjectId(String id) { return findByProjectId(id).isPresent() ? 1 : 0; }
    public boolean existsByProjectId(String id) { return findByProjectId(id).isPresent(); }
}
