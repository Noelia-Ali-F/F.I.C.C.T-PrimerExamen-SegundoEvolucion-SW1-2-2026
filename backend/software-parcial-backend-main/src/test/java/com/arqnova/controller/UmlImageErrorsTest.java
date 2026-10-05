package com.arqnova.controller;

import com.arqnova.service.OpenAiUmlInterpreter;
import com.arqnova.service.UmlAiCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class UmlImageErrorsTest {
    private final OpenAiUmlInterpreter openAi = mock(OpenAiUmlInterpreter.class);
    private final UmlAiCommandController controller = new UmlAiCommandController(mock(UmlAiCommandService.class), openAi);
    private final MockMultipartFile image = new MockMultipartFile("image","uml.png","image/png",new byte[]{1});

    @Test void timeoutControlado() throws Exception {
        when(openAi.analyzeImage(image)).thenThrow(new java.net.http.HttpTimeoutException("request timed out"));
        assertThat(controller.analyzeImage(image).getBody()).isEqualTo(Map.of("code","AI_PROVIDER_UNAVAILABLE","message","El servicio de análisis con IA no está disponible temporalmente. Inténtalo nuevamente más tarde."));
    }
    @Test void cuotaControlada() throws Exception {
        when(openAi.analyzeImage(image)).thenThrow(new OpenAiUmlInterpreter.ProviderException(429));
        var result=controller.analyzeImage(image);
        assertThat(result.getStatusCode().value()).isEqualTo(429);
        assertThat(((Map<?,?>)result.getBody()).get("code")).isEqualTo("AI_RATE_LIMITED");
    }
    @Test void imagenInvalidaNoConsultaProveedor() {
        var result=controller.analyzeImage(new MockMultipartFile("image","uml.txt","text/plain",new byte[]{1}));
        assertThat(result.getStatusCode().value()).isEqualTo(400);
        assertThat(((Map<?,?>)result.getBody()).get("code")).isEqualTo("INVALID_IMAGE");
        verifyNoInteractions(openAi);
    }
    @Test void sinClases() throws Exception {
        when(openAi.analyzeImage(image)).thenReturn(Map.of("status","ERROR"));
        assertThat(((Map<?,?>)controller.analyzeImage(image).getBody()).get("code")).isEqualTo("NO_UML_DETECTED");
    }
    @Test void jsonInvalido() throws Exception {
        when(openAi.analyzeImage(image)).thenThrow(new IllegalArgumentException("schema"));
        assertThat(((Map<?,?>)controller.analyzeImage(image).getBody()).get("code")).isEqualTo("AI_INVALID_RESPONSE");
    }
    @Test void ready() throws Exception {
        when(openAi.analyzeImage(image)).thenReturn(Map.of("status","READY"));
        assertThat(controller.analyzeImage(image).getStatusCode().value()).isEqualTo(200);
    }
}
