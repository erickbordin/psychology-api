package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * O contrato OpenAPI de onde o front gera os tipos (spec 9). Precisa ser
 * alcancavel sem autenticacao — mas so ele, nenhuma rota de dado junto.
 */
@HttpTest
class OpenApiHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("o contrato OpenAPI e alcancavel sem autenticacao")
    void contratoEPublico() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.openapi").exists());
    }

    @Test
    @DisplayName("o contrato descreve os endpoints da Fatia 1")
    void contratoDescreveAFatia1() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
               .andExpect(jsonPath("$.paths['/painel/hoje']").exists())
               .andExpect(jsonPath("$.paths['/consultas/conflito']").exists())
               .andExpect(jsonPath("$.paths['/auth/login']").exists());
    }

    @Test
    @DisplayName("expor o contrato nao abre nenhuma rota de dado")
    void contratoNaoAbreRotaDeDado() throws Exception {
        // sem token: /pacientes continua fechado
        mockMvc.perform(get("/pacientes"))
               .andExpect(status().isUnauthorized());
    }
}
