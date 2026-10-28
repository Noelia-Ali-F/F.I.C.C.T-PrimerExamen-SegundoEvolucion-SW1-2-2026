package com.arqnova.repositorios.dynamodb;

import com.arqnova.modelo.Departamento;
import com.arqnova.servicios.puertos.salida.DepartamentoStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;

@Repository @Profile("dynamodb")
public class DynamoDepartmentStore implements DepartamentoStore {
    private final DynamoDocumentStore store;
    public DynamoDepartmentStore(DynamoDocumentStore store) { this.store = store; }
    public Optional<Departamento> findById(String id) { return store.get("DEPARTMENT", id, Departamento.class); }
    public Optional<Departamento> findByNombreIgnoreCase(String name) {
        return store.get("UNIQUE", DynamoDocumentStore.uniqueKey("department-name", name), DynamoDocumentStore.Unique.class).flatMap(v -> findById(v.owner()));
    }
    public boolean existsByNombreIgnoreCase(String name) { return findByNombreIgnoreCase(name).isPresent(); }
    public List<Departamento> findAllByOrderByNombreAsc() { return store.list("DEPARTMENT", Departamento.class).stream().sorted(Comparator.comparing(Departamento::getNombre)).toList(); }
    public List<Departamento> findAllByActivoTrueOrderByNombreAsc() { return findAllByOrderByNombreAsc().stream().filter(Departamento::isActivo).toList(); }
    public Departamento save(Departamento v) {
        return store.atomic(() -> {
            if (v.getId() == null || v.getId().isBlank()) v.setId(UUID.randomUUID().toString());
            var old = findById(v.getId());
            old.filter(o -> !o.getNombre().equalsIgnoreCase(v.getNombre())).ifPresent(o -> store.release("department-name", o.getNombre(), v.getId()));
            store.reserve("department-name", v.getNombre(), v.getId());
            v.setFechaCreacion(old.map(Departamento::getFechaCreacion).orElse(v.getFechaCreacion() == null ? LocalDateTime.now() : v.getFechaCreacion()));
            return store.put("DEPARTMENT", v.getId(), v);
        });
    }
    public List<Departamento> saveAll(Iterable<Departamento> values) { List<Departamento> result = new ArrayList<>(); values.forEach(v -> result.add(save(v))); return result; }
    public void delete(Departamento v) { store.atomic(() -> { findById(v.getId()).ifPresent(o -> store.release("department-name", o.getNombre(), v.getId())); store.delete("DEPARTMENT", v.getId()); return null; }); }
    public long count() { return findAllByOrderByNombreAsc().size(); }
    public void deleteAll() { findAllByOrderByNombreAsc().forEach(this::delete); }
}
