package com.arqnova.dto.response;

import com.arqnova.domain.enums.RolUsuario;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos públicos de usuario para administración")
public class UsuarioAdminResponse {

    @Schema(description = "ID interno del usuario")
    private String id;

    @Schema(description = "Nombre de usuario", example = "ana.colaboradora")
    private String username;

    @Schema(description = "Nombre completo", example = "Ana Colaboradora")
    private String nombreCompleto;

    @Schema(description = "Rol interno del usuario. En la interfaz, los roles no administrativos se presentan como Usuario/Colaborador", example = "SOLICITANTE")
    private RolUsuario rol;

    @Schema(description = "Departamento asociado")
    private String departamento;

    @Schema(description = "Fecha de creación")
    private LocalDateTime fechaCreacion;

    @Schema(description = "URL de la foto de perfil")
    private String avatarUrl;

    @Schema(description = "Indica si la cuenta puede iniciar sesión")
    private boolean habilitado;
}
