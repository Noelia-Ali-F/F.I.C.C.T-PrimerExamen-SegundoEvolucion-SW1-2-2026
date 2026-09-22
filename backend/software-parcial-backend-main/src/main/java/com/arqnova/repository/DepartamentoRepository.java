package com.arqnova.repository;

import com.arqnova.domain.model.Departamento;
import com.arqnova.persistence.jpa.entity.DepartmentEntity;
import com.arqnova.persistence.jpa.repository.DepartmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository @RequiredArgsConstructor
public class DepartamentoRepository {
    private final DepartmentJpaRepository repository;
    public boolean existsByNombreIgnoreCase(String n){return repository.existsByNameIgnoreCase(n);}
    public Optional<Departamento> findByNombreIgnoreCase(String n){return repository.findByNameIgnoreCase(n).map(this::domain);}
    public List<Departamento> findAllByActivoTrueOrderByNombreAsc(){return repository.findAllByActiveTrueOrderByNameAsc().stream().map(this::domain).toList();}
    public List<Departamento> findAllByOrderByNombreAsc(){return repository.findAllByOrderByNameAsc().stream().map(this::domain).toList();}
    public Optional<Departamento> findById(String id){return repository.findById(id).map(this::domain);}
    public Departamento save(Departamento v){if(v.getId()==null||v.getId().isBlank())v.setId(java.util.UUID.randomUUID().toString());return domain(repository.save(entity(v)));}
    public List<Departamento> saveAll(Iterable<Departamento> values){java.util.ArrayList<Departamento> result=new java.util.ArrayList<>();values.forEach(v->result.add(save(v)));return result;}
    public void delete(Departamento v){repository.deleteById(v.getId());}
    public long count(){return repository.count();}
    public void deleteAll(){repository.deleteAll();}
    private Departamento domain(DepartmentEntity e){return Departamento.builder().id(e.getId()).nombre(e.getName()).descripcion(e.getDescription()).creadoPor(e.getCreatedBy()).activo(e.isActive()).fechaCreacion(e.getCreatedAt()).build();}
    private DepartmentEntity entity(Departamento v){return DepartmentEntity.builder().id(v.getId()).name(v.getNombre()).description(v.getDescripcion()).createdBy(v.getCreadoPor()).active(v.isActivo()).createdAt(v.getFechaCreacion()).updatedAt(java.time.LocalDateTime.now()).build();}
}
