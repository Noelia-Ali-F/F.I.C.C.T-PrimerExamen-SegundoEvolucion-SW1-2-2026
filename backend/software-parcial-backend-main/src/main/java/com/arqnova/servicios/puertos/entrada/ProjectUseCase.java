package com.arqnova.servicios.puertos.entrada;
import com.arqnova.modelo.enums.RolUsuario;
import com.arqnova.modelo.entidades.ProjectEntity;
import com.arqnova.modelo.entidades.ProjectMemberEntity;
import com.arqnova.modelo.entidades.ProjectMemberId;
import com.arqnova.modelo.entidades.ProjectMemberRole;
import com.arqnova.modelo.entidades.ProjectStatus;
import com.arqnova.modelo.entidades.UmlDiagramEntity;
import com.arqnova.servicios.puertos.salida.ProjectDataPort;
import com.arqnova.servicios.puertos.salida.ProjectMemberDataPort;
import com.arqnova.servicios.puertos.salida.UmlDiagramDataPort;
import com.arqnova.servicios.puertos.salida.UserDataPort;
import com.arqnova.modelo.excepciones.DuplicateResourceException;
import com.arqnova.modelo.excepciones.ResourceNotFoundException;
import com.arqnova.modelo.excepciones.UnauthorizedActionException;
import lombok.RequiredArgsConstructor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;
public interface ProjectUseCase {
 ProjectEntity createProject(String id, String name, String description, String ownerUserId) ;
 Optional<ProjectEntity> findProjectById(String projectId) ;
 UmlDiagramEntity saveDiagram(String diagramId, String projectId, Map<String, Object> model) ;
 UmlDiagramEntity saveDiagram(String diagramId, String projectId, Map<String, Object> model,
                                        Integer expectedVersion) ;
 Optional<UmlDiagramEntity> findDiagramByProjectId(String projectId) ;
 ProjectMemberEntity addMember(String projectId, String userId, ProjectMemberRole role) ;
 ProjectMemberEntity acceptInvitation(String projectId, String userId, ProjectMemberRole invitedRole) ;
 void deleteProject(String projectId) ;
 List<ProjectEntity> listAccessible(String userId) ;
 ProjectMemberRole requireAccess(String projectId, String userId, ProjectMemberRole... allowed) ;
 ProjectEntity updateProject(String projectId, String userId, String name, String description,
                                       ProjectStatus status) ;
 void deleteProjectAuthorized(String projectId, String userId) ;

}
