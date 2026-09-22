package com.arqnova.domain.model;

import com.arqnova.domain.enums.RolUsuario;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    private String id;

    private String username;

    private String password;

    private String nombreCompleto;

    private RolUsuario rol;

    private String departamento;

    private String avatarUrl;

    /** Los documentos anteriores a CU1 no tienen este campo y se consideran habilitados. */
    @Builder.Default
    private Boolean habilitado = true;

    @CreatedDate
    private LocalDateTime fechaCreacion;
}
