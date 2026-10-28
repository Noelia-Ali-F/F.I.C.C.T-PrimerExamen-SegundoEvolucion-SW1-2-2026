package com.arqnova.modelo.entidades;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberEntity {

    private ProjectMemberId id;

    private ProjectMemberRole memberRole;

    private LocalDateTime createdAt;

}
