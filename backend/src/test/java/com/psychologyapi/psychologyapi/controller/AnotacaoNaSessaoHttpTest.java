package com.psychologyapi.psychologyapi.controller;

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

/**
 * POST /pacientes/{id}/anotacoes com `consultaId` — a anotacao passa a ser
 * DAQUELA SESSAO (spec 5.1). O campo e opcional: anotacao avulsa continua
 * valida, so nao entra na linha do tempo da ficha.
 */
@HttpTest
class AnotacaoNaSessaoHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private ConsultaRepository consultaRepository;

    private String tokenDoDono;
    private Paciente helena;
    private Consulta sessaoDaHelena;
    private Consulta sessaoDoRui;
    private Consulta sessaoDeOutroPsicologo;

    @BeforeEach
    void preparar() {
        Usuario dono = usuarioRepository.save(Fixtures.usuario("dona.anotacao@example.com"));
        tokenDoDono = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());

        helena = pacienteRepository.save(Fixtures.paciente(dono, "Helena Vaz"));
        Paciente rui = pacienteRepository.save(Fixtures.paciente(dono, "Rui Sampaio"));

        sessaoDaHelena = consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-12-01T09:00:00"), Status.REALIZADA));
        sessaoDoRui = consultaRepository.save(Fixtures.consulta(
                dono, rui, LocalDateTime.parse("2026-12-01T10:00:00"), Status.REALIZADA));

        Usuario outro = usuarioRepository.save(Fixtures.usuario("outro.anotacao@example.com"));
        Paciente doOutro = pacienteRepository.save(Fixtures.paciente(outro, "Paciente do outro psicologo"));
        sessaoDeOutroPsicologo = consultaRepository.save(Fixtures.consulta(
                outro, doOutro, LocalDateTime.parse("2026-12-01T11:00:00"), Status.REALIZADA));
    }

    private String corpo(String conteudo, UUID consultaId) {
        String idEmJson = consultaId == null ? "null" : "\"" + consultaId + "\"";
        return "{\"anotacao\":\"" + conteudo + "\",\"consultaId\":" + idEmJson + "}";
    }

    @Test
    @DisplayName("anotacao com consultaId volta presa aquela sessao")
    void anotacaoSePrendeASessao() throws Exception {
        mockMvc.perform(post("/pacientes/" + helena.getId() + "/anotacoes")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Trabalhamos a exposicao gradual.", sessaoDaHelena.getId())))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.consultaId").value(sessaoDaHelena.getId().toString()));
    }

    @Test
    @DisplayName("anotacao avulsa continua valida e volta com consultaId nulo")
    void anotacaoAvulsaContinuaValida() throws Exception {
        mockMvc.perform(post("/pacientes/" + helena.getId() + "/anotacoes")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Liguei fora do horario.", null)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.consultaId").doesNotExist());
    }

    @Test
    @DisplayName("nao prende anotacao a sessao de OUTRO paciente do mesmo psicologo")
    void naoPrendeASessaoDeOutroPaciente() throws Exception {
        mockMvc.perform(post("/pacientes/" + helena.getId() + "/anotacoes")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Anotacao trocada de ficha.", sessaoDoRui.getId())))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.mensagem").value(
                       "Esta sessão é de outro paciente. A anotação não pode ser presa a ela."))
               .andExpect(jsonPath("$.erros").isEmpty());
    }

    @Test
    @DisplayName("sessao de outro psicologo responde 404, nao 409 — nem confirma que existe")
    void sessaoDeOutroPsicologoNaoExisteParaMim() throws Exception {
        mockMvc.perform(post("/pacientes/" + helena.getId() + "/anotacoes")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Tentativa de alcancar outra agenda.", sessaoDeOutroPsicologo.getId())))
               .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("consultaId inexistente responde 404")
    void consultaInexistente() throws Exception {
        mockMvc.perform(post("/pacientes/" + helena.getId() + "/anotacoes")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Aponta pro nada.", UUID.randomUUID())))
               .andExpect(status().isNotFound());
    }
}
