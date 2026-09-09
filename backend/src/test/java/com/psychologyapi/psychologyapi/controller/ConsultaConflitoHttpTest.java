package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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

@HttpTest
class ConsultaConflitoHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private ConsultaRepository consultaRepository;

    private String tokenDoDono;
    private UUID consultaDaHelenaId;
    private UUID outraConsultaDoDonoId;

    @BeforeEach
    void preparar() {
        Usuario dono = usuarioRepository.save(Fixtures.usuario("dona.conflito@example.com"));
        tokenDoDono = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());

        Paciente helena = pacienteRepository.save(Fixtures.paciente(dono, "Helena Vaz"));
        consultaDaHelenaId = consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-12-02T14:00:00"), Status.AGENDADA)).getId();
        consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-12-04T11:00:00"), Status.CANCELADA));

        // outra consulta do MESMO dono, em outro horario — serve para provar
        // que excluir um id QUE NAO E o ocupante nao apaga o conflito real
        outraConsultaDoDonoId = consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-12-06T09:00:00"), Status.AGENDADA)).getId();

        Usuario outro = usuarioRepository.save(Fixtures.usuario("outro.conflito@example.com"));
        Paciente doOutro = pacienteRepository.save(Fixtures.paciente(outro, "Paciente do outro psicologo"));
        consultaRepository.save(Fixtures.consulta(
                outro, doOutro, LocalDateTime.parse("2026-12-03T10:00:00"), Status.AGENDADA));
    }

    @Test
    @DisplayName("horario livre responde livre=true e sem paciente")
    void horarioLivre() throws Exception {
        mockMvc.perform(get("/consultas/conflito")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .param("dtConsulta", "2026-12-01T09:00:00"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.livre").value(true))
               .andExpect(jsonPath("$.pacienteNome").doesNotExist());
    }

    @Test
    @DisplayName("horario ocupado responde livre=false e diz QUEM esta nele, com o id de quem ocupa")
    void horarioOcupadoDizQuem() throws Exception {
        // consulta existente da Helena Vaz em 2026-12-02T14:00, criada no setup
        mockMvc.perform(get("/consultas/conflito")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .param("dtConsulta", "2026-12-02T14:00:00"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.livre").value(false))
               .andExpect(jsonPath("$.pacienteNome").value("Helena Vaz"))
               .andExpect(jsonPath("$.consultaId").value(consultaDaHelenaId.toString()));
    }

    @Test
    @DisplayName("consulta de OUTRO psicologo no mesmo horario nao gera conflito")
    void agendaDeOutroPsicologoNaoConflita() throws Exception {
        // mesma hora da consulta do outro psicologo, criada no setup
        mockMvc.perform(get("/consultas/conflito")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .param("dtConsulta", "2026-12-03T10:00:00"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.livre").value(true));
    }

    @Test
    @DisplayName("consulta cancelada nao ocupa o horario")
    void canceladaNaoOcupa() throws Exception {
        mockMvc.perform(get("/consultas/conflito")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .param("dtConsulta", "2026-12-04T11:00:00"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.livre").value(true));
    }

    @Test
    @DisplayName("horario dentro da janela de 50min de uma consulta existente NAO esta livre")
    void janelaDeCinquentaMinutosOcupa() throws Exception {
        // existe consulta da Helena as 14h00; as 14h30 cai dentro da janela.
        // Responder "livre" aqui faria o POST recusar em seguida — o front
        // perguntaria, receberia sim, enviaria e apanharia
        mockMvc.perform(get("/consultas/conflito")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .param("dtConsulta", "2026-12-02T14:30:00"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.livre").value(false))
               .andExpect(jsonPath("$.pacienteNome").value("Helena Vaz"));
    }

    @Test
    @DisplayName("fora da janela de 50min o horario esta livre")
    void foraDaJanelaEstaLivre() throws Exception {
        // 15h00 esta a 60 minutos das 14h00: fora da janela
        mockMvc.perform(get("/consultas/conflito")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .param("dtConsulta", "2026-12-02T15:00:00"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.livre").value(true));
    }

    @Test
    @DisplayName("excluir a propria consulta da checagem libera o horario dela mesma — dialogo de edicao "
            + "revalidando o horario nao pode encontrar a si mesmo como conflito")
    void excluirAPropriaConsultaLiberaOHorario() throws Exception {
        mockMvc.perform(get("/consultas/conflito")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .param("dtConsulta", "2026-12-02T14:00:00")
                .param("excluirConsultaId", consultaDaHelenaId.toString()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.livre").value(true));
    }

    @Test
    @DisplayName("excluir uma consulta DIFERENTE da ocupante nao esconde o conflito real — "
            + "o parametro nao pode virar um jeito de silenciar conflito verdadeiro")
    void excluirConsultaDiferenteNaoEscondeOConflito() throws Exception {
        mockMvc.perform(get("/consultas/conflito")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                .param("dtConsulta", "2026-12-02T14:00:00")
                .param("excluirConsultaId", outraConsultaDoDonoId.toString()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.livre").value(false))
               .andExpect(jsonPath("$.pacienteNome").value("Helena Vaz"))
               .andExpect(jsonPath("$.consultaId").value(consultaDaHelenaId.toString()));
    }
}
