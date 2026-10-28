package com.arqnova.servicios.puertos.salida;
import com.arqnova.modelo.entidades.*;
import java.util.*;
public interface ProjectDataPort extends EntityDataPort<ProjectEntity,String> {
 List<ProjectEntity> findAllByOrderByUpdatedAtDesc();
 List<ProjectEntity> findByIdInOrderByUpdatedAtDesc(Collection<String> ids);
 boolean existsByOwnerUserIdAndNameIgnoreCase(String ownerUserId,String name);
 boolean existsByOwnerUserIdAndNameIgnoreCaseAndIdNot(String ownerUserId,String name,String id);
}
