package com.psychologyapi.psychologyapi.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * PATCH /consultas/{id}/status — marca Realizada/Faltou em um clique (spec
 * 4①), sem reenviar dtConsulta. Ver ConsultaStatusDTO para o porque de nao
 * reaproveitar o PUT.
 */
@HttpTest
class ConsultaStatusHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private ConsultaRepository consultaRepository;

    private String tokenDoDono;
    private UUID consultaId;
    private UUID consultaDeOutroId;

    @BeforeEach
    void preparar() {
        Usuario dono = usuarioRepository.save(Fixtures.usuario("dona.status@example.com"));
        tokenDoDono = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());

        Paciente helena = pacienteRepository.save(Fixtures.paciente(dono, "Helena Vaz"));
        consultaId = consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-12-02T14:00:00"), Status.AGENDADA)).getId();

        Usuario outro = usuarioRepository.save(Fixtures.usuario("outro.status@example.com"));
        Paciente doOutro = pacienteRepository.save(Fixtures.paciente(outro, "Paciente do outro psicologo"));
        consultaDeOutroId = consultaRepository.save(Fixtures.consulta(
                outro, doOutro, LocalDateTime.parse("2026-12-05T09:00:00"), Status.AGENDADA)).getId();
    }

    @Test
    @DisplayName("PATCH marca a sessao como realizada sem mexer na data")
    void marcarRealizada() throws Exception {
        mockMvc.perform(patch("/consultas/" + consultaId + "/status")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"status":"REALIZADA"}
                         """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("REALIZADA"))
               .andExpect(jsonPath("$.dtConsulta").value(startsWith("2026-12-02T14:00")));
    }

    @Test
    @DisplayName("PATCH sem status responde 400")
    void statusAusenteResponde400() throws Exception {
        mockMvc.perform(patch("/consultas/" + consultaId + "/status")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
               .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH nao pode reabrir uma consulta cancelada como agendada: isso "
            + "burlaria a checagem de conflito e permitiria duplo agendamento no mesmo horario")
    void reabrirComoAgendadaPorAquiEBloqueadoParaEvitarDuploAgendamento() throws Exception {
        mockMvc.perform(patch("/consultas/" + consultaId + "/status")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"status":"AGENDADA"}
                         """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("status")));
    }

    @Test
    @DisplayName("status inexistente no enum responde 400 no envelope do projeto, nao no corpo generico do Spring")
    void statusInexistenteNoEnumRespondeNoEnvelopeDoProjeto() throws Exception {
        mockMvc.perform(patch("/consultas/" + consultaId + "/status")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"status":"BANANA"}
                         """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.mensagem").exists())
               .andExpect(jsonPath("$.erros").isArray());
    }

    @Test
    @DisplayName("consulta de outro psicologo responde 404, nao 403")
    void consultaDeOutroPsicologoResponde404() throws Exception {
        mockMvc.perform(patch("/consultas/" + consultaDeOutroId + "/status")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"status":"FALTOU"}
                         """))
               .andExpect(status().isNotFound());
    }
}
