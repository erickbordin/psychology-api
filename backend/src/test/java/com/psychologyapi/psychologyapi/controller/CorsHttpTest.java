package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * O frontend vive em outra origem. Sem CORS o navegador nem chega a enviar a
 * requisicao real, e o erro aparece no console do cliente, nao no log da API.
 */
@HttpTest
class CorsHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("preflight de origem permitida responde com os cabecalhos de CORS")
    void preflightDeOrigemPermitida() throws Exception {
        mockMvc.perform(options("/pacientes")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("preflight de origem desconhecida e recusado")
    void preflightDeOrigemDesconhecida() throws Exception {
        mockMvc.perform(options("/pacientes")
                .header(HttpHeaders.ORIGIN, "http://malicioso.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }
}
