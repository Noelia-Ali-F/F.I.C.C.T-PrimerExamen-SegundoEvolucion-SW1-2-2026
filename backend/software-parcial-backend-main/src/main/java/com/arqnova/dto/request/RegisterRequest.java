package com.arqnova.dto.request;

import com.arqnova.domain.enums.RolUsuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Petición de registro de nuevo usuario")
public class RegisterRequest {

    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Email(message = "El correo electrónico no tiene un formato válido")
    @Schema(description = "Correo electrónico usado como identificador", example = "maria@correo.com")
    private String username;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @Schema(description = "Contraseña que será procesada de forma segura", example = "Test1234")
    private String password;

    @NotBlank(message = "El nombre completo es obligatorio")
    @Schema(description = "Nombre completo del usuario", example = "Maria Lopez")
    private String nombreCompleto;

    @Schema(description = "Rol interno asignado por el servidor; no se acepta desde el registro público", accessMode = Schema.AccessMode.READ_ONLY)
    private RolUsuario rol;

    @Schema(description = "Dato interno de compatibilidad asignado por el servidor", accessMode = Schema.AccessMode.READ_ONLY)
    private String departamento;
}
