package com.arqnova.persistence.jpa.service;

import com.arqnova.domain.enums.RolUsuario;
import com.arqnova.persistence.jpa.entity.ProjectEntity;
import com.arqnova.persistence.jpa.entity.ProjectMemberEntity;
import com.arqnova.persistence.jpa.entity.ProjectMemberId;
import com.arqnova.persistence.jpa.entity.ProjectMemberRole;
import com.arqnova.persistence.jpa.entity.ProjectStatus;
import com.arqnova.persistence.jpa.entity.UmlDiagramEntity;
import com.arqnova.persistence.jpa.repository.ProjectJpaRepository;
import com.arqnova.persistence.jpa.repository.ProjectMemberJpaRepository;
import com.arqnova.persistence.jpa.repository.UmlDiagramJpaRepository;
import com.arqnova.persistence.jpa.repository.UserJpaRepository;
import com.arqnova.exception.DuplicateResourceException;
import com.arqnova.exception.ResourceNotFoundException;
import com.arqnova.exception.UnauthorizedActionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UmlProjectPersistenceService {

    private final ProjectJpaRepository projectRepository;
    private final ProjectMemberJpaRepository memberRepository;
    private final UmlDiagramJpaRepository diagramRepository;
    private final UserJpaRepository userRepository;

    @Transactional
    public ProjectEntity createProject(String id, String name, String description, String ownerUserId) {
        requireText(id, "projectId");
        requireText(name, "name");
        requireText(ownerUserId, "ownerUserId");
        if (projectRepository.existsById(id)) {
            throw new DuplicateResourceException("Ya existe un proyecto con el ID indicado");
        }
        String normalizedName = name.trim();
        if (projectRepository.existsByOwnerUserIdAndNameIgnoreCase(ownerUserId, normalizedName)) {
            throw new DuplicateResourceException("Ya existe un proyecto UML con ese nombre");
        }

        ProjectEntity project = projectRepository.save(ProjectEntity.builder()
                .id(id)
                .name(normalizedName)
                .description(description == null ? null : description.trim())
                .status(ProjectStatus.ACTIVE)
                .ownerUserId(ownerUserId)
                .build());

        ProjectMemberId ownerId = new ProjectMemberId(id, ownerUserId);
        if (!memberRepository.existsById(ownerId)) {
            memberRepository.save(ProjectMemberEntity.builder()
                    .id(ownerId)
                    .memberRole(ProjectMemberRole.OWNER)
                    .build());
        }
        return project;
    }

    @Transactional(readOnly = true)
    public Optional<ProjectEntity> findProjectById(String projectId) {
        return projectRepository.findById(projectId);
    }

    @Transactional
    public UmlDiagramEntity saveDiagram(String diagramId, String projectId, Map<String, Object> model) {
        return saveDiagram(diagramId, projectId, model, null);
    }

    @Transactional
    public UmlDiagramEntity saveDiagram(String diagramId, String projectId, Map<String, Object> model,
                                        Integer expectedVersion) {
        requireText(diagramId, "diagramId");
        requireText(projectId, "projectId");
        if (model == null) throw new IllegalArgumentException("model es obligatorio");
        if (!projectRepository.existsById(projectId)) {
            throw new IllegalArgumentException("El proyecto indicado no existe");
        }

        UmlDiagramEntity diagram = diagramRepository.findByProjectIdForUpdate(projectId)
                .map(existing -> {
                    if (expectedVersion != null && expectedVersion != existing.getModelVersion()) {
                        throw new DuplicateResourceException("El diagrama fue actualizado por otra sesión. Recarga antes de guardar");
                    }
                    existing.setModel(new LinkedHashMap<>(model));
                    existing.setModelVersion(existing.getModelVersion() + 1);
                    return existing;
                })
                .orElseGet(() -> UmlDiagramEntity.builder()
                        .id(diagramId)
                        .projectId(projectId)
                        .model(new LinkedHashMap<>(model))
                        .modelVersion(1)
                        .build());
        if (expectedVersion != null && diagram.getModelVersion() == 1 && expectedVersion != 0) {
            throw new DuplicateResourceException("La versión inicial esperada del diagrama debe ser 0");
        }
        ProjectEntity project = projectRepository.findById(projectId).orElseThrow();
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(project);
        return diagramRepository.save(diagram);
    }

    @Transactional(readOnly = true)
    public Optional<UmlDiagramEntity> findDiagramByProjectId(String projectId) {
        return diagramRepository.findByProjectId(projectId);
    }

    @Transactional
    public ProjectMemberEntity addMember(String projectId, String userId, ProjectMemberRole role) {
        requireText(projectId, "projectId");
        requireText(userId, "userId");
        if (role == null) throw new IllegalArgumentException("memberRole es obligatorio");
        if (!projectRepository.existsById(projectId)) {
            throw new IllegalArgumentException("El proyecto indicado no existe");
        }

        ProjectMemberId id = new ProjectMemberId(projectId, userId);
        return memberRepository.findById(id)
                .map(existing -> {
                    existing.setMemberRole(role);
                    return memberRepository.save(existing);
                })
                .orElseGet(() -> memberRepository.save(ProjectMemberEntity.builder()
                        .id(id)
                        .memberRole(role)
                        .build()));
    }

    @Transactional
    public ProjectMemberEntity acceptInvitation(String projectId, String userId, ProjectMemberRole invitedRole) {
        ProjectMemberId id = new ProjectMemberId(projectId, userId);
        return memberRepository.findById(id)
                .map(existing -> {
                    // Una invitación nunca reduce permisos ya concedidos ni puede alterar al OWNER.
                    if (existing.getMemberRole() == ProjectMemberRole.OWNER
                            || (existing.getMemberRole() == ProjectMemberRole.EDITOR && invitedRole == ProjectMemberRole.VIEWER)) {
                        return existing;
                    }
                    existing.setMemberRole(invitedRole);
                    return memberRepository.save(existing);
                })
                .orElseGet(() -> addMember(projectId, userId, invitedRole));
    }

    @Transactional
    public void deleteProject(String projectId) {
        if (projectRepository.existsById(projectId)) {
            projectRepository.deleteById(projectId);
            projectRepository.flush();
        }
    }

    @Transactional(readOnly = true)
    public List<ProjectEntity> listAccessible(String userId) {
        if (isAdministrator(userId)) {
            return projectRepository.findAllByOrderByUpdatedAtDesc();
        }
        List<String> projectIds = memberRepository.findByIdUserId(userId).stream()
                .map(member -> member.getId().getProjectId()).toList();
        return projectIds.isEmpty() ? List.of() : projectRepository.findByIdInOrderByUpdatedAtDesc(projectIds);
    }

    @Transactional(readOnly = true)
    public ProjectMemberRole requireAccess(String projectId, String userId, ProjectMemberRole... allowed) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Proyecto", "id", projectId);
        }
        if (isAdministrator(userId)) {
            return ProjectMemberRole.OWNER;
        }
        ProjectMemberRole role = memberRepository.findByIdProjectIdAndIdUserId(projectId, userId)
                .map(ProjectMemberEntity::getMemberRole)
                .orElseThrow(() -> new UnauthorizedActionException("No tienes acceso a este proyecto"));
        if (allowed.length > 0 && !Set.of(allowed).contains(role)) {
            throw new UnauthorizedActionException("Tu membresía no permite realizar esta operación");
        }
        return role;
    }

    @Transactional
    public ProjectEntity updateProject(String projectId, String userId, String name, String description,
                                       ProjectStatus status) {
        requireAccess(projectId, userId, ProjectMemberRole.OWNER);
        ProjectEntity project = projectRepository.findById(projectId).orElseThrow();
        if (name != null) {
            requireText(name, "name");
            String normalizedName = name.trim();
            if (projectRepository.existsByOwnerUserIdAndNameIgnoreCaseAndIdNot(
                    project.getOwnerUserId(), normalizedName, projectId)) {
                throw new DuplicateResourceException("Ya existe un proyecto UML con ese nombre");
            }
            project.setName(normalizedName);
        }
        if (description != null) project.setDescription(description.trim());
        if (status != null) project.setStatus(status);
        return projectRepository.save(project);
    }

    @Transactional
    public void deleteProjectAuthorized(String projectId, String userId) {
        requireAccess(projectId, userId, ProjectMemberRole.OWNER);
        projectRepository.deleteById(projectId);
        projectRepository.flush();
    }

    private boolean isAdministrator(String userId) {
        return userRepository.findByUsernameIgnoreCase(userId)
                .map(user -> user.getRole() == RolUsuario.ADMINISTRADOR)
                .orElse(false);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " es obligatorio");
        }
    }
}
