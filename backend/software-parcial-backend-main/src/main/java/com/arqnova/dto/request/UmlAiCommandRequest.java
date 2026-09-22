package com.arqnova.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UmlAiCommandRequest {
    @NotBlank(message = "La instrucción es obligatoria")
    private String instruction;

    @NotNull(message = "El modelo UML actual es obligatorio")
    private Map<String, Object> model;
}
