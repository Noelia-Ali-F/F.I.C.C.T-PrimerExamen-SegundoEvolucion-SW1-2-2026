package com.arqnova.controller;

import com.arqnova.dto.request.UmlAiCommandRequest;
import com.arqnova.dto.response.ApiResponse;
import com.arqnova.dto.response.UmlAiCommandResponse;
import com.arqnova.service.UmlAiCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import com.arqnova.service.OpenAiUmlInterpreter;

@RestController
@RequestMapping("/api/v1/uml-ai")
@RequiredArgsConstructor
public class UmlAiCommandController {
    private final UmlAiCommandService service;
    private final OpenAiUmlInterpreter openAi;

    @PostMapping("/interpret")
    public ResponseEntity<ApiResponse<UmlAiCommandResponse>> interpret(
            @Valid @RequestBody UmlAiCommandRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Instrucción UML interpretada", service.interpret(request)));
    }

    @PostMapping(value = "/image", consumes = "multipart/form-data")
    public ResponseEntity<?> analyzeImage(@RequestParam("image") MultipartFile image) {
        if (image.isEmpty() || image.getSize() > 10L * 1024 * 1024
                || !java.util.Set.of("image/png","image/jpeg","image/webp").contains(String.valueOf(image.getContentType()).toLowerCase(java.util.Locale.ROOT))
                || !String.valueOf(image.getOriginalFilename()).toLowerCase(java.util.Locale.ROOT).matches(".*\\.(png|jpg|jpeg|webp)$")) {
            return imageError(400,"INVALID_IMAGE","El archivo seleccionado no es una imagen válida.");
        }
        try {
            Map<String,Object> result = openAi.analyzeImage(image);
            if (!"READY".equals(result.get("status")))
                return imageError(422,"NO_UML_DETECTED","No se pudieron identificar clases UML con suficiente claridad en la imagen.");
            return ResponseEntity.ok(ApiResponse.ok("Imagen UML analizada", result));
        } catch (OpenAiUmlInterpreter.ProviderException error) {
            if (error.httpStatus == 400 || error.httpStatus == 401 || error.httpStatus == 403)
                return imageError(502,"AI_PROVIDER_REJECTED","El servicio de IA rechazó el análisis. Contacta al administrador.");
            return error.httpStatus == 429
                    ? imageError(429,"AI_RATE_LIMITED","El servicio de IA alcanzó temporalmente su límite de solicitudes. Inténtalo más tarde.")
                    : imageError(503,"AI_PROVIDER_UNAVAILABLE","El servicio de análisis con IA no está disponible temporalmente. Inténtalo nuevamente más tarde.");
        } catch (java.net.http.HttpTimeoutException | java.net.ConnectException error) {
            return imageError(503,"AI_PROVIDER_UNAVAILABLE","El servicio de análisis con IA no está disponible temporalmente. Inténtalo nuevamente más tarde.");
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException error) {
            return imageError(502,"AI_INVALID_RESPONSE","El servicio de IA devolvió una respuesta inválida. Inténtalo nuevamente.");
        } catch (IllegalStateException error) {
            return imageError(502,"AI_INVALID_RESPONSE","El servicio de IA no pudo entregar un resultado válido.");
        } catch (Exception error) {
            return imageError(500,"INTERNAL_ERROR","No se pudo completar el análisis. Inténtalo nuevamente.");
        }
    }

    private ResponseEntity<Map<String,String>> imageError(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code",code,"message",message));
    }
}
