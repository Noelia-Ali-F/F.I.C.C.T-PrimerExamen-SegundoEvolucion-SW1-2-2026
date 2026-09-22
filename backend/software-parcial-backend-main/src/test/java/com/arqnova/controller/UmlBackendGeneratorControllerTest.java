package com.arqnova.controller;

import com.arqnova.service.impl.UmlBackendGeneratorServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UmlBackendGeneratorControllerTest {
    @Test
    void endpointAcceptsCurrentEditorPayloadAndReturnsZip() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new UmlBackendGeneratorController(new UmlBackendGeneratorServiceImpl())).build();
        String json = """
                {"projectName":"cliente-api","basePackage":"com.acme.cliente","includeBasicConfig":true,
                 "model":{"format":"uml-class-diagram","version":2,"title":"Modelo","classes":[
                   {"id":"cliente","name":"Cliente","x":100,"y":200,"position":{"x":100,"y":200},"data":{"selected":true},
                    "attributes":[{"id":"id","name":"id","type":"Long","isPrimaryKey":true,"position":{}}]}],"relations":[]}}
                """;

        mvc.perform(post("/api/v1/uml-backend/generate").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("X-Generated-Classes", "1"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"cliente-api.zip\""));

        mvc.perform(post("/api/v1/uml-backend/postman").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Postman-Entities", "1"))
                .andExpect(header().string("X-Postman-Requests", "5"))
                .andExpect(jsonPath("$.info.name").value("cliente-api - API Spring Boot"))
                .andExpect(jsonPath("$.item[0].item.length()").value(5));
    }
}
