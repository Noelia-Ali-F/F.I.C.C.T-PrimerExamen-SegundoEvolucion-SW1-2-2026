package com.arqnova.modelo.entidades;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UmlDiagramEntity {

    private String id;

    private String projectId;

    @Builder.Default
    private Map<String, Object> model = new LinkedHashMap<>();

    @Builder.Default
    private int modelVersion = 1;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
