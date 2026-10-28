package com.arqnova.servicios.puertos.salida;
import com.arqnova.modelo.entidades.*;
import java.util.*;
public interface ProjectMemberDataPort extends EntityDataPort<ProjectMemberEntity,ProjectMemberId> {
 long countByIdProjectId(String projectId);
 Optional<ProjectMemberEntity> findByIdProjectIdAndIdUserId(String projectId,String userId);
 List<ProjectMemberEntity> findByIdUserId(String userId);
}
