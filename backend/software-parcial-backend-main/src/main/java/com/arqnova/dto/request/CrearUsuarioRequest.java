package com.arqnova.dto.request;

import com.arqnova.domain.enums.RolUsuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Petición para crear un usuario desde administración")
public class CrearUsuarioRequest {

    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Schema(description = "Nombre de usuario único", example = "ana.colaboradora")
    private String username;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @Schema(description = "Contraseña temporal procesada de forma segura", example = "Temporal123")
    private String password;

    @NotBlank(message = "El nombre completo es obligatorio")
    @Schema(description = "Nombre completo del usuario", example = "Ana Colaboradora")
    private String nombreCompleto;

    @NotNull(message = "El rol del usuario es obligatorio")
    @Schema(description = "Rol interno del usuario. SOLICITANTE corresponde al actor visible Usuario/Colaborador", example = "SOLICITANTE")
    private RolUsuario rol;

    @Schema(description = "Dato interno de compatibilidad para usuarios no administradores", example = "Sistemas")
    private String departamento;
}
