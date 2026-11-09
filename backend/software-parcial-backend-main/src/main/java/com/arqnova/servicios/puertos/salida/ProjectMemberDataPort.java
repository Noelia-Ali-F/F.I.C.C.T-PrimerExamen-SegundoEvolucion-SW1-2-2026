package com.arqnova.servicios.puertos.salida;
import com.arqnova.modelo.entidades.*;
import java.util.*;
public interface ProjectMemberDataPort extends EntityDataPort<ProjectMemberEntity,ProjectMemberId> {
 default void lockProject(String projectId) {}
 default Optional<String> lockRole(String projectId,String userId) {
  return findByIdProjectIdAndIdUserId(projectId,userId).map(member -> member.getMemberRole().name());
 }
 long countByIdProjectId(String projectId);
 Optional<ProjectMemberEntity> findByIdProjectIdAndIdUserId(String projectId,String userId);
 List<ProjectMemberEntity> findByIdUserId(String userId);
}
