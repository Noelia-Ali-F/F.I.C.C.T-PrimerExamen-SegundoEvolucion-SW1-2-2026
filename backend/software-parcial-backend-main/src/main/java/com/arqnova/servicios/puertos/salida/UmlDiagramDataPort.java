package com.arqnova.servicios.puertos.salida;
import com.arqnova.modelo.entidades.*;
import java.util.*;
public interface UmlDiagramDataPort extends EntityDataPort<UmlDiagramEntity,String> {
 Optional<UmlDiagramEntity> findByProjectId(String projectId);
 Optional<UmlDiagramEntity> findByProjectIdForUpdate(String projectId);
 long countByProjectId(String projectId);
 boolean existsByProjectId(String projectId);
}
