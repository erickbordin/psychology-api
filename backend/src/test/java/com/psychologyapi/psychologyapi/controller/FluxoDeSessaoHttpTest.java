package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psychologyapi.psychologyapi.authsecurity.CookieRefreshToken;
import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * Nenhum teste por endpoint prova que login, chamada autenticada e renovacao
 * ENCAIXAM — foi assim que o bug do AnotacaoMapper passou despercebido.
 */
@HttpTest
class FluxoDeSessaoHttpTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String tokenDe(MvcResult resultado) throws Exception {
        JsonNode corpo = objectMapper.readTree(resultado.getResponse().getContentAsString());
        return corpo.get("token").asText();
    }

    @Test
    @DisplayName("registrar, logar, usar o token, renovar e usar o token novo")
    void fluxoCompletoDeSessao() throws Exception {
        mockMvc.perform(post("/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoConvite\":\"teste-1\",\"aceiteTermos\":true,\"versaoTermos\":\"teste-1\",\"nome\":\"Ana Sessao\",\"email\":\"sessao@teste.com\",\"senha\":\"senhaforte123\"}"))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailUsuario\":\"sessao@teste.com\",\"senha\":\"senhaforte123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String primeiroAcesso = tokenDe(login);
        Cookie refresh = login.getResponse().getCookie(CookieRefreshToken.NOME);

        mockMvc.perform(get("/pacientes").header(HttpHeaders.AUTHORIZATION, "Bearer " + primeiroAcesso))
                .andExpect(status().isOk());

        MvcResult renovacao = mockMvc.perform(post("/auth/refresh").cookie(refresh))
                .andExpect(status().isOk())
                .andReturn();

        String segundoAcesso = tokenDe(renovacao);

        mockMvc.perform(get("/pacientes").header(HttpHeaders.AUTHORIZATION, "Bearer " + segundoAcesso))
                .andExpect(status().isOk());
    }
}
