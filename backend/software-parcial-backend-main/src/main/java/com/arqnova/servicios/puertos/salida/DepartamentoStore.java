package com.arqnova.servicios.puertos.salida;
import com.arqnova.modelo.Departamento;
import java.util.*;
public interface DepartamentoStore {
 boolean existsByNombreIgnoreCase(String n);
 Optional<Departamento> findByNombreIgnoreCase(String n);
 List<Departamento> findAllByActivoTrueOrderByNombreAsc();
 List<Departamento> findAllByOrderByNombreAsc();
 Optional<Departamento> findById(String id);
 Departamento save(Departamento v);
 List<Departamento> saveAll(Iterable<Departamento> values);
 void delete(Departamento v);
 long count();
 void deleteAll();
}
