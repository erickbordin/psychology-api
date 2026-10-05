package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.support.HttpTest;

@HttpTest
@TestPropertySource(properties = "app.limite.por-minuto=3")
class LimiteDeTentativasHttpTest {

    private static final String LOGIN = "{\"emailUsuario\":\"ninguem@teste.com\",\"senha\":\"senhaerrada123\"}";

    @Autowired
    private MockMvc mockMvc;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor doIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    @DisplayName("o quarto login do mesmo IP responde 429 no envelope de erro")
    void quartoLoginRecebe429() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.mensagem").value("Muitas tentativas. Tente de novo em alguns minutos."))
                .andExpect(jsonPath("$.erros").isArray())
                .andExpect(jsonPath("$.erros.length()").value(0));
    }

    @Test
    @DisplayName("X-Forwarded-For enviado direto a API nao muda a chave do limite")
    void cabecalhoForjadoNaoEscapaDoLimite() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/refresh")
                    .header("X-Forwarded-For", "1.2.3." + i))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/refresh")
                .header("X-Forwarded-For", "1.2.3.4"))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(post("/auth/refresh")
                .header("X-Forwarded-For", "1.2.3.4"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("rota fora da lista nao e limitada mesmo acima do limite")
    void rotaForaDaListaNaoELimitada() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/pacientes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().is(org.hamcrest.Matchers.anyOf(
                            org.hamcrest.Matchers.is(401), org.hamcrest.Matchers.is(403))));
        }
    }

    @Test
    @DisplayName("caminho com letra codificada conta no mesmo limite do login")
    void caminhoCodificadoContaNoLimite() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post(URI.create("/auth/%6cogin")).with(doIp("10.9.0.1")).contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/auth/login").with(doIp("10.9.0.1")).contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(URI.create("/auth/%6cogin")).with(doIp("10.9.0.1")).contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(post("/auth/login").with(doIp("10.9.0.1")).contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("barra final e barra dupla no registrar contam no mesmo limite e nunca escapam dele")
    void variantesDeBarraNaoEscapam() throws Exception {
        String[] variantes = { "/auth/registrar/", "/auth//registrar", "/auth/registrar" };
        int limitadas = 0;
        for (int i = 0; i < 8; i++) {
            int status = mockMvc.perform(post(URI.create(variantes[i % variantes.length])).with(doIp("10.9.0.2"))
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse().getStatus();
            if (status == 429) {
                limitadas++;
            }
        }
        org.assertj.core.api.Assertions.assertThat(limitadas).isEqualTo(5);
    }
}
