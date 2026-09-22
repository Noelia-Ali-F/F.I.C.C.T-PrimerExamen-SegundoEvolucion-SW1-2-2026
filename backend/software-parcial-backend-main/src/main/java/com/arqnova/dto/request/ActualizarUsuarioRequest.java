package com.arqnova.dto.request;

import com.arqnova.domain.enums.RolUsuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Petición para actualizar un usuario desde administración")
public class ActualizarUsuarioRequest {

    @NotBlank(message = "El nombre completo es obligatorio")
    @Schema(description = "Nombre completo del usuario", example = "Ana Patricia Colaboradora")
    private String nombreCompleto;

    @NotNull(message = "El rol del usuario es obligatorio")
    @Schema(description = "Rol interno del usuario. SOLICITANTE corresponde al actor visible Usuario/Colaborador", example = "SOLICITANTE")
    private RolUsuario rol;

    @Schema(description = "Dato interno de compatibilidad para usuarios no administradores", example = "Sistemas")
    private String departamento;

    @Schema(description = "Contraseña nueva opcional. Si se omite, se conserva la actual", example = "nueva-clave")
    private String password;

    @Schema(description = "URL de la foto de perfil", example = "https://example.com/avatar.png")
    private String avatarUrl;
}
