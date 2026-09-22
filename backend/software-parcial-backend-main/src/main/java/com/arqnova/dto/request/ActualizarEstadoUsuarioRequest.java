package com.arqnova.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActualizarEstadoUsuarioRequest {
    @NotNull(message = "El estado habilitado es obligatorio")
    private Boolean habilitado;
}
