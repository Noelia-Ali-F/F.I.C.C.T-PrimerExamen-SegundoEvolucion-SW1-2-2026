package com.arqnova.controller;

import com.arqnova.service.impl.UmlFlutterGeneratorServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UmlFlutterGeneratorControllerTest {
    @Test
    void returnsDownloadableFlutterZipFromCurrentEditorModel() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new UmlFlutterGeneratorController(new UmlFlutterGeneratorServiceImpl())).build();
        String json = """
                {"projectName":"clientes_mobile","backendBaseUrl":"http://10.0.2.2:8080",
                 "model":{"classes":[{"id":"cliente","name":"Cliente","x":100,"y":200,"data":{},
                 "attributes":[{"id":"id","name":"id","type":"Long","isPrimaryKey":true}]}],"relations":[]}}
                """;

        mvc.perform(post("/api/v1/uml-flutter/generate").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("X-Generated-Models", "1"))
                .andExpect(header().string("X-Generated-Screens", "3"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"clientes_mobile-flutter.zip\""));
    }
}
