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

    private static final String CAMPO_DATA_DO_PAINEL =
            "$.components.schemas.SessaoDoDiaDTO.properties.dtConsulta";

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

    /**
     * Re-revisao C2 (2026-09-09, laudo-c1-c2-rodada-2.md): o conserto
     * anterior corrigiu so os quatro campos de ENTRADA de LocalDateTime e
     * deixou todo campo de SAIDA declarado como {"type":"string","format":
     * "date-time"} — RFC 3339 com offset, na convencao OpenAPI/JSON Schema —
     * enquanto a API nunca emite offset (spec 5.9). Fechado globalmente em
     * OpenApiConfig via SpringDocUtils.replaceWithSchema(LocalDateTime.class,
     * ...). SessaoDoDiaDTO.dtConsulta e o campo de data de /painel/hoje, a
     * tela que e o proprio motivo da Fatia 1 existir — se algum LocalDateTime
     * de saida ficar de fora do registro global, este e o mais provavel de
     * doer primeiro.
     */
    @Test
    @DisplayName("um campo de data de SAIDA (/painel/hoje) declara string sem offset, "
            + "sem mais 'format: date-time'")
    void campoDeDataDeSaidaDoPainelDeclaraFormatoSemOffset() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
               .andExpect(jsonPath(CAMPO_DATA_DO_PAINEL + ".type").value("string"))
               .andExpect(jsonPath(CAMPO_DATA_DO_PAINEL + ".format").doesNotExist())
               .andExpect(jsonPath(CAMPO_DATA_DO_PAINEL + ".example").value("2026-12-02T14:00:00"));
    }
}
