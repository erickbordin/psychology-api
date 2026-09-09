package com.psychologyapi.psychologyapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    /** Identifica o contrato de onde o front gera os tipos (spec 9). */
    @Bean
    public OpenAPI contrato() {
        return new OpenAPI().info(new Info()
                .title("Psychology API")
                .version("v1")
                .description("API do consultorio: agenda, pacientes, prontuario e financeiro."));
    }
}
