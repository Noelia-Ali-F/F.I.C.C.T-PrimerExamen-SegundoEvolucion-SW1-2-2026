package com.arqnova.controladores;
import com.arqnova.servicios.puertos.entrada.ProjectPushUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
class ProjectPushControllerTest {
    @Test void deviceOwnerComesFromAuthenticationInsteadOfTheRequestBody()throws Exception{
        var service=mock(ProjectPushUseCase.class);var mvc=MockMvcBuilders.standaloneSetup(new ProjectPushController(service)).build();
        mvc.perform(post("/api/v1/push/devices").principal(()->"noelia").contentType("application/json").content("{\"fid\":\"fid-test-device\"}")).andExpect(status().isOk());
        verify(service).register("noelia","fid-test-device");
        mvc.perform(post("/api/v1/push/devices/remove").principal(()->"noelia").contentType("application/json").content("{\"fid\":\"fid-test-device\"}")).andExpect(status().isOk());
        verify(service).unregister("noelia","fid-test-device");
    }
}
