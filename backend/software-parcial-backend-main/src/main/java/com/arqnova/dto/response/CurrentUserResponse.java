package com.arqnova.dto.response;

import com.arqnova.domain.enums.RolUsuario;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserResponse {
    private String username;
    private String nombreCompleto;
    private RolUsuario rol;
    private String departamento;
    private String avatarUrl;
    private boolean habilitado;
}
