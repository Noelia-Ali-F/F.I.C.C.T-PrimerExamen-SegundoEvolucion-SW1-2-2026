package com.arqnova.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI arqNovaOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ArqNova - Plataforma UML Colaborativa")
                        .description("API REST de ArqNova para diseñar diagramas UML, colaborar en tiempo real, aplicar operaciones asistidas por inteligencia artificial y generar backends Spring Boot con recursos Postman.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Equipo de Desarrollo")
                                .email("soporte@arqnova.com"))
                        .license(new License().name("Apache 2.0").url("http://springdoc.org")));
    }
}
