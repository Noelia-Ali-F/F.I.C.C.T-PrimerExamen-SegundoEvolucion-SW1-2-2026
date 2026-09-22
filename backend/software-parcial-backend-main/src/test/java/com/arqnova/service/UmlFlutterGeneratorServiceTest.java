package com.arqnova.service;

import com.arqnova.dto.request.UmlBackendGenerationRequest.*;
import com.arqnova.dto.request.UmlFlutterGenerationRequest;
import com.arqnova.service.impl.UmlFlutterGeneratorServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class UmlFlutterGeneratorServiceTest {
    private final UmlFlutterGeneratorServiceImpl service = new UmlFlutterGeneratorServiceImpl();

    @Test
    void generatesIndependentFlutterProjectWithModelsRestServicesAndCrudScreens() throws Exception {
        var cliente = new UmlClass("cliente", "Cliente", List.of(
                new UmlAttribute("id", "id", "Long", true),
                new UmlAttribute("nombre", "nombre", "String", false),
                new UmlAttribute("fecha", "fechaRegistro", "DateTime", false)));
        var producto = new UmlClass("producto", "Producto", List.of(
                new UmlAttribute("id-producto", "id", "UUID", true),
                new UmlAttribute("precio", "precio", "BigDecimal", false)));
        var request = new UmlFlutterGenerationRequest("ventas_mobile", "http://localhost:8080",
                new UmlModel(List.of(cliente, producto), List.of()));

        var generated = service.generate(request);
        var files = unzip(generated.content());

        assertEquals("ventas_mobile-flutter.zip", generated.fileName());
        assertEquals(2, generated.modelCount());
        assertEquals(5, generated.screenCount());
        assertTrue(files.containsKey("ventas_mobile/pubspec.yaml"));
        assertTrue(files.containsKey("ventas_mobile/lib/models/cliente.dart"));
        assertTrue(files.containsKey("ventas_mobile/lib/services/cliente_api_service.dart"));
        assertTrue(files.containsKey("ventas_mobile/lib/screens/cliente_list_page.dart"));
        assertTrue(files.containsKey("ventas_mobile/lib/screens/cliente_form_page.dart"));
        assertTrue(files.get("ventas_mobile/lib/models/cliente.dart").contains("final DateTime? fechaRegistro"));
        assertTrue(files.get("ventas_mobile/lib/services/cliente_api_service.dart").contains("/api/cliente"));
        assertTrue(files.get("ventas_mobile/lib/config/api_config.dart").contains("API_BASE_URL"));
        assertFalse(files.keySet().stream().anyMatch(path -> path.contains("arqnova-mobile")));
    }

    @Test
    void rejectsEmptyModelAndUnsupportedTypes() {
        var empty = new UmlFlutterGenerationRequest("app", null, new UmlModel(List.of(), List.of()));
        assertEquals("El diagrama debe contener al menos una clase para generar Flutter.",
                assertThrows(IllegalArgumentException.class, () -> service.generate(empty)).getMessage());

        var invalid = new UmlClass("c", "Cliente", List.of(new UmlAttribute("a", "nombre", "varchar", false)));
        var request = new UmlFlutterGenerationRequest("app", null, new UmlModel(List.of(invalid), List.of()));
        assertEquals("La clase Cliente contiene el atributo nombre con tipo varchar no soportado.",
                assertThrows(IllegalArgumentException.class, () -> service.generate(request)).getMessage());
    }

    private static Map<String, String> unzip(byte[] content) throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
        }
        return files;
    }
}
