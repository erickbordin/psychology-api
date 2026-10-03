package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

@HttpTest
class ConsultaComBaixaHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private ConsultaRepository consultaRepository;

    private String token;
    private Usuario dono;
    private Paciente helena;

    @BeforeEach
    void preparar() {
        dono = usuarioRepository.save(Fixtures.usuario("dona.baixa@example.com"));
        token = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());
        Paciente paciente = Fixtures.paciente(dono, "Helena Vaz");
        paciente.setValorSessao(new BigDecimal("180.00"));
        helena = pacienteRepository.save(paciente);
    }

    private Consulta paga() {
        Consulta consulta = Fixtures.consulta(dono, helena, LocalDateTime.parse("2026-12-02T14:00:00"), Status.REALIZADA);
        consulta.setValor(new BigDecimal("180.00"));
        consulta.setPagoEm(LocalDateTime.parse("2026-12-02T15:00:00"));
        return consultaRepository.save(consulta);
    }

    @Test
    @DisplayName("PATCH para REALIZADA devolve o valor congelado do paciente")
    void realizadaDevolveValor() throws Exception {
        Consulta agendada = consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-12-03T14:00:00"), Status.AGENDADA));

        mockMvc.perform(patch("/consultas/" + agendada.getId() + "/status")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"status":"REALIZADA"}
                         """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.valor").value(180.0))
               .andExpect(jsonPath("$.pagoEm").doesNotExist());
    }

    @Test
    @DisplayName("GET da consulta devolve valor e pagoEm")
    void getDevolveDinheiro() throws Exception {
        Consulta consulta = paga();

        mockMvc.perform(get("/consultas/" + consulta.getId()).header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.valor").value(180.0))
               .andExpect(jsonPath("$.pagoEm").value("2026-12-02T15:00:00"));
    }

    @Test
    @DisplayName("PATCH FALTOU numa sessao paga responde 409 com a frase que diz o que fazer")
    void pagaNaoViraFaltou() throws Exception {
        Consulta consulta = paga();

        mockMvc.perform(patch("/consultas/" + consulta.getId() + "/status")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"status":"FALTOU"}
                         """))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.mensagem")
                       .value("Esta sessão já tem baixa. Desfaça a baixa antes de mudar o status."))
               .andExpect(jsonPath("$.erros").isEmpty());
    }

    @Test
    @DisplayName("PUT com status CANCELADA numa sessao paga responde 409")
    void pagaNaoCancelaPeloPut() throws Exception {
        Consulta consulta = paga();

        mockMvc.perform(put("/consultas/" + consulta.getId())
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"status":"CANCELADA"}
                         """))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.mensagem")
                       .value("Esta sessão já tem baixa. Desfaça a baixa antes de mudar o status."));
    }

    @Test
    @DisplayName("PUT so com dtConsulta numa sessao paga responde 200 e mantem status, valor e pagoEm")
    void putSoComDataMantemOResto() throws Exception {
        Consulta consulta = paga();

        mockMvc.perform(put("/consultas/" + consulta.getId())
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"dtConsulta":"2026-12-09T14:00:00"}
                         """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("REALIZADA"))
               .andExpect(jsonPath("$.valor").value(180.0))
               .andExpect(jsonPath("$.pagoEm").value("2026-12-02T15:00:00"))
               .andExpect(jsonPath("$.dtConsulta").value(org.hamcrest.Matchers.startsWith("2026-12-09T14:00")));
    }

    @Test
    @DisplayName("PUT so com status REALIZADA numa agendada congela o valor e mantem a data")
    void putSoComStatusCongelaEMantemData() throws Exception {
        Consulta agendada = consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-12-04T14:00:00"), Status.AGENDADA));

        mockMvc.perform(put("/consultas/" + agendada.getId())
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"status":"REALIZADA"}
                         """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.valor").value(180.0))
               .andExpect(jsonPath("$.dtConsulta").value(org.hamcrest.Matchers.startsWith("2026-12-04T14:00")));
    }

    @Test
    @DisplayName("DELETE numa sessao paga responde 409 e ela continua na agenda")
    void pagaNaoSeExclui() throws Exception {
        Consulta consulta = paga();

        mockMvc.perform(delete("/consultas/" + consulta.getId()).header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.mensagem")
                       .value("Esta sessão tem baixa registrada. Desfaça a baixa antes de excluir a sessão."));

        mockMvc.perform(get("/consultas/" + consulta.getId()).header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isOk());
    }
}
