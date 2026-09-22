package com.arqnova.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class CreateUmlSessionRequest {
    @NotBlank
    private String projectId;
    @NotBlank
    private String projectName;
    @NotNull
    private Map<String, Object> model;
    /** Rol otorgado por la invitación. Si se omite se usa EDITOR. */
    private String inviteRole;
}
