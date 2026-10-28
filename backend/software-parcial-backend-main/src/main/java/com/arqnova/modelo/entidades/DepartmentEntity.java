package com.arqnova.modelo.entidades;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DepartmentEntity {
      private String id;
     private String name;
     private String description;
     private String createdBy;
     private boolean active;
     private LocalDateTime createdAt;
     private LocalDateTime updatedAt;
}
