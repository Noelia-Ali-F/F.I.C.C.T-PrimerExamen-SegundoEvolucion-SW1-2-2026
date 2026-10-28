package com.arqnova.servicios.puertos.salida;
import java.util.*;
/** Storage contract expressed only in application/domain types. */
public interface EntityDataPort<T,K> {
 Optional<T> findById(K id);
 boolean existsById(K id);
 T save(T value);
 void deleteById(K id);
 List<T> findAll();
 long count();
 void deleteAll();
 void flush();
}
