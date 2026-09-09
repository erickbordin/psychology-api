package com.psychologyapi.psychologyapi.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private Usuario dono;
    private Paciente helena;
    private UUID consultaId;
    private UUID consultaDeOutroId;

    @BeforeEach
    void preparar() {
        dono = usuarioRepository.save(Fixtures.usuario("dona.status@example.com"));
        tokenDoDono = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());

        helena = pacienteRepository.save(Fixtures.paciente(dono, "Helena Vaz"));
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
    @DisplayName("PATCH so aceita REALIZADA/FALTOU como destino (@StatusDeFechamento): AGENDADA e "
            + "recusado com 400 de validacao de campo. Isto fecha o mesmo destino que o PUT ja "
            + "protegia — NAO e o que evita duplo agendamento; essa guarda fica em atualizarStatus, "
            + "sobre o status ATUAL da consulta (ver naoPodeSairDeCanceladaQuandoHorarioFoiRealocado)")
    void destinoAgendadaERecusadoPeloStatusDeFechamento() throws Exception {
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

    @Test
    @DisplayName("PATCH marca a sessao como faltou sem mexer na data, numa consulta que NAO esta cancelada "
            + "— a guarda de CANCELADA nao pode quebrar o proposito normal da rota")
    void marcarFaltouEmConsultaNaoCancelada() throws Exception {
        mockMvc.perform(patch("/consultas/" + consultaId + "/status")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"status":"FALTOU"}
                         """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("FALTOU"))
               .andExpect(jsonPath("$.dtConsulta").value(startsWith("2026-12-02T14:00")));
    }

    /**
     * Sequencia medida no laudo do conjunto (C1): cancelar A libera o horario
     * (todo caminho de escrita trata CANCELADA como vago); criar B no mesmo
     * horario entao funciona; e sem a guarda, PATCH A para FALTOU/REALIZADA
     * reabriria A, deixando A e B ativas as duas no mesmo horario — duas
     * sessoes na mesma linha da agenda, sem aviso nenhum.
     */
    @Test
    @DisplayName("PATCH nao pode tirar uma consulta de CANCELADA para FALTOU quando o horario ja foi "
            + "dado a outra pessoa: 409, com a mensagem que diz o que fazer, e a agenda do dia continua "
            + "com exatamente uma sessao ativa naquele horario — nao duas")
    void naoPodeSairDeCanceladaParaFaltouQuandoHorarioFoiRealocado() throws Exception {
        LocalDateTime horario = LocalDateTime.parse("2026-12-15T14:00:00");

        UUID consultaCanceladaId = consultaRepository.save(Fixtures.consulta(
                dono, helena, horario, Status.CANCELADA)).getId();

        Paciente outroPaciente = pacienteRepository.save(Fixtures.paciente(dono, "Otavio Lins"));

        mockMvc.perform(post("/consultas")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"pacienteId":"%s","dtConsulta":"2026-12-15T14:00:00"}
                         """.formatted(outroPaciente.getId())))
               .andExpect(status().isCreated());

        mockMvc.perform(patch("/consultas/" + consultaCanceladaId + "/status")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"status":"FALTOU"}
                         """))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.mensagem")
                       .value("Esta sessão foi cancelada. Reagende-a para poder marcar presença."));

        // a consequencia que importa: a agenda do dia continua com UMA so
        // sessao ATIVA as 14h — a cancelada nao voltou a ocupar o horario
        mockMvc.perform(get("/consultas")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .param("de", "2026-12-15")
                .param("ate", "2026-12-15"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[?(@.status != 'CANCELADA')]", hasSize(1)))
               .andExpect(jsonPath("$[?(@.status == 'CANCELADA')]", hasSize(1)));
    }

    @Test
    @DisplayName("PATCH nao pode tirar uma consulta de CANCELADA para REALIZADA tambem: os dois status "
            + "de fechamento tem que ser recusados, nao so FALTOU")
    void naoPodeSairDeCanceladaParaRealizada() throws Exception {
        UUID consultaCanceladaId = consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-12-16T10:00:00"), Status.CANCELADA)).getId();

        mockMvc.perform(patch("/consultas/" + consultaCanceladaId + "/status")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"status":"REALIZADA"}
                         """))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.mensagem")
                       .value("Esta sessão foi cancelada. Reagende-a para poder marcar presença."));
    }
}
