package com.arqnova.modelo.entidades;

import com.arqnova.modelo.enums.RolUsuario;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserEntity {
      private String id;
     private String username;
     private String passwordHash;
     private String fullName;
      private RolUsuario role;
     private String department;
     private String avatarUrl;
     private boolean enabled;
     private LocalDateTime createdAt;
     private LocalDateTime updatedAt;

}
