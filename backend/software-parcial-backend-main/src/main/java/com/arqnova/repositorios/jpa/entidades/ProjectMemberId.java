package com.arqnova.repositorios.jpa.entidades;

import com.arqnova.modelo.entidades.ProjectStatus;
import com.arqnova.modelo.entidades.ProjectMemberRole;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberId implements Serializable {

    @Column(name = "project_id", length = 128, nullable = false)
    private String projectId;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;
}
