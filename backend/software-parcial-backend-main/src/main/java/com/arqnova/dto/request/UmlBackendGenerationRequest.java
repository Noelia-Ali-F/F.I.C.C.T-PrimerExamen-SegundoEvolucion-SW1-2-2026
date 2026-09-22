package com.arqnova.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UmlBackendGenerationRequest(
        String projectName,
        String basePackage,
        Boolean includeBasicConfig,
        UmlModel model
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UmlModel(List<UmlClass> classes, List<UmlRelation> relations) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UmlClass(String id, String name, List<UmlAttribute> attributes) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UmlAttribute(String id, String name, String type, Boolean isPrimaryKey) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UmlRelation(String id, String sourceId, String targetId, String type,
                              String label, String sourceMultiplicity, String targetMultiplicity) {}
}
