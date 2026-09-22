package com.arqnova.service;

import com.arqnova.dto.request.UmlBackendGenerationRequest;
import com.arqnova.dto.request.UmlBackendGenerationRequest.*;
import com.arqnova.service.impl.UmlBackendGeneratorServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class UmlBackendGeneratorServiceTest {
    private final UmlBackendGeneratorServiceImpl service = new UmlBackendGeneratorServiceImpl();

    @Test
    void ignoresEditorPositionAndMetadataFieldsWhenDeserializingRequest() throws Exception {
        String json = """
                {
                  "projectName":"editor-api","basePackage":"com.acme.editor","includeBasicConfig":true,
                  "uiState":{"zoom":0.82},
                  "model":{"format":"uml-class-diagram","version":2,"title":"Editor","updatedAt":"2026-09-20T20:00:00Z",
                    "classes":[{"id":"cliente","name":"Cliente","x":100,"y":200,"position":{"x":100,"y":200},"data":{"color":"blue"},
                      "description":"metadata","attributes":[{"id":"a1","name":"id","type":"Long","isPrimaryKey":true,"selected":false,"data":{}}],"methods":[]}],
                    "relations":[]}}
                """;
        var request = new ObjectMapper().findAndRegisterModules().readValue(json, UmlBackendGenerationRequest.class);

        var generated = service.generate(request);
        var files = unzip(generated.content());

        assertEquals(1, generated.classCount());
        assertTrue(files.keySet().stream().anyMatch(path -> path.endsWith("model/Cliente.java")));
        assertTrue(files.get("editor-api/src/main/java/com/acme/editor/model/Cliente.java").contains("@Id"));
    }

    @Test
    void generatesExecutableLayersAndJpaRelationsFromUml() throws Exception {
        var cliente = clazz("cliente", "Cliente", attr("cliente-id", "id", "Long", true), attr("cliente-nombre", "nombre", "String", false));
        var producto = clazz("producto", "Producto", attr("producto-id", "id", "Long", true));
        var venta = clazz("venta", "Venta", attr("venta-id", "id", "Long", true), attr("fecha", "fecha", "Date", false));
        var relations = List.of(
                new UmlRelation("r1", "cliente", "venta", "association", "realiza", "1", "0..*"),
                new UmlRelation("producto", "producto", "venta", "association", "incluye", "1", "0..*")
        );
        var request = new UmlBackendGenerationRequest("ventas-api", "com.acme.ventas", true,
                new UmlModel(List.of(cliente, producto, venta), relations));

        var generated = service.generate(request);
        Map<String,String> files = unzip(generated.content());

        assertEquals(3, generated.classCount());
        assertTrue(files.keySet().stream().anyMatch(path -> path.endsWith("model/Cliente.java")));
        assertTrue(files.keySet().stream().anyMatch(path -> path.endsWith("repository/VentaRepository.java")));
        assertTrue(files.keySet().stream().anyMatch(path -> path.endsWith("service/VentaService.java")));
        assertTrue(files.keySet().stream().anyMatch(path -> path.endsWith("controller/VentaController.java")));
        assertTrue(files.get("ventas-api/src/main/java/com/acme/ventas/model/Cliente.java").contains("@OneToMany"));
        assertTrue(files.get("ventas-api/src/main/java/com/acme/ventas/model/Venta.java").contains("@ManyToOne"));
        assertTrue(files.get("ventas-api/src/main/java/com/acme/ventas/model/Venta.java").contains("LocalDate fecha"));
        assertTrue(files.get("ventas-api/pom.xml").contains("spring-boot-starter-data-jpa"));
        assertTrue(files.get("ventas-api/src/main/resources/application.properties").contains("jdbc:postgresql"));
        assertGeneratedJavaCompiles(files);

        var postman = service.generatePostman(request);
        assertTrue(files.containsKey("ventas-api/postman/collection.json"));
        assertEquals(new String(postman.content(), StandardCharsets.UTF_8), files.get("ventas-api/postman/collection.json"));
        var collection = new ObjectMapper().readTree(postman.content());
        assertEquals(3, postman.entityCount());
        assertEquals(15, postman.requestCount());
        assertEquals(3, collection.path("item").size());
        assertEquals(5, collection.path("item").get(0).path("item").size());
        assertEquals("GET", collection.path("item").get(0).path("item").get(0).path("request").path("method").asText());
        assertEquals("POST", collection.path("item").get(0).path("item").get(2).path("request").path("method").asText());
        assertTrue(collection.toString().contains("{{baseUrl}}/api/cliente"));
        assertTrue(collection.toString().contains("Content-Type"));
        assertTrue(collection.toString().contains("nombre"));
    }

    @Test
    void rejectsMissingTypesAndOrphanRelations() {
        var invalid = clazz("cliente", "Cliente", attr("a", "nombre", "", false));
        var missingType = assertThrows(IllegalArgumentException.class, () -> service.generate(new UmlBackendGenerationRequest("api", "com.acme", true, new UmlModel(List.of(invalid), List.of()))));
        assertEquals("La clase Cliente contiene el atributo nombre sin tipo de dato.", missingType.getMessage());
        var valid = clazz("cliente", "Cliente", attr("a", "id", "Long", true));
        var orphan = new UmlRelation("r", "cliente", "missing", "association", "", "1", "*");
        var orphanError = assertThrows(IllegalArgumentException.class, () -> service.generate(new UmlBackendGenerationRequest("api", "com.acme", true, new UmlModel(List.of(valid), List.of(orphan)))));
        assertEquals("La relación r tiene targetId missing que no corresponde a ninguna clase.", orphanError.getMessage());
        var unsupported = clazz("profesional", "profesional", attr("email", "email", "varchar", false));
        var typeError = assertThrows(IllegalArgumentException.class, () -> service.generate(new UmlBackendGenerationRequest("api", "com.acme", true, new UmlModel(List.of(unsupported), List.of()))));
        assertEquals("La clase profesional contiene el atributo email con tipo varchar no soportado.", typeError.getMessage());
    }

    private static UmlClass clazz(String id, String name, UmlAttribute... attributes) { return new UmlClass(id, name, List.of(attributes)); }
    private static UmlAttribute attr(String id, String name, String type, boolean pk) { return new UmlAttribute(id, name, type, pk); }
    private static Map<String,String> unzip(byte[] zip) throws Exception {
        Map<String,String> files = new LinkedHashMap<>();
        try (var input = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry())
                files.put(entry.getName(), new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        return files;
    }

    private static void assertGeneratedJavaCompiles(Map<String,String> files) throws Exception {
        Path source = Files.createTempDirectory("cu9-generated-source");
        Path output = Files.createTempDirectory("cu9-generated-classes");
        List<String> javaFiles = new ArrayList<>();
        for (var file : files.entrySet()) {
            if (!file.getKey().endsWith(".java")) continue;
            Path target = source.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
            javaFiles.add(target.toString());
        }
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Se requiere un JDK para validar el proyecto generado");
        List<String> args = new ArrayList<>(List.of("-classpath", System.getProperty("java.class.path"), "-d", output.toString()));
        args.addAll(javaFiles);
        assertEquals(0, compiler.run(null, null, null, args.toArray(String[]::new)), "El código Java generado debe compilar");
    }
}
