package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.model.Anotacao;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.AnotacaoRepository;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * DELETE /consultas/{id} recusa a exclusao quando a sessao tem anotacao viva
 * presa a ela (decisao do dono, revisao da Fatia 2).
 *
 * O defeito que isto fecha: a exclusao logica da consulta NAO apagava a
 * anotacao, mas a ficha ({@code FichaService}) so lista consulta nao apagada —
 * entao o conteudo clinico continuava no banco e sumia do prontuario, sem
 * aviso nenhum. Registro com prazo de guarda legal (spec 7.3) nao pode
 * desaparecer de vista por efeito colateral de outra exclusao.
 */
@HttpTest
class ConsultaComAnotacaoHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private ConsultaRepository consultaRepository;
    @Autowired private AnotacaoRepository anotacaoRepository;

    private String token;
    private Paciente helena;
    private Consulta sessaoAnotada;
    private Consulta sessaoSemAnotacao;
    private Anotacao anotacaoDaSessao;

    @BeforeEach
    void preparar() {
        Usuario dono = usuarioRepository.save(Fixtures.usuario("dona.exclusao.sessao@example.com"));
        token = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());

        helena = pacienteRepository.save(Fixtures.paciente(dono, "Helena Vaz"));

        sessaoAnotada = consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-11-18T09:00:00"), Status.REALIZADA));
        sessaoSemAnotacao = consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-11-25T09:00:00"), Status.AGENDADA));

        anotacaoDaSessao = anotacaoRepository.save(Fixtures.anotacaoDaSessao(
                dono, helena, sessaoAnotada, "Trabalhamos a exposicao gradual."));
    }

    @Test
    @DisplayName("excluir sessao com anotacao viva responde 409 e diz como resolver")
    void sessaoComAnotacaoNaoPodeSerExcluida() throws Exception {
        mockMvc.perform(delete("/consultas/" + sessaoAnotada.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.mensagem").value(
                       "Esta sessão tem anotação registrada. Exclua a anotação antes de excluir a sessão."))
               .andExpect(jsonPath("$.erros").isEmpty());
    }

    @Test
    @DisplayName("recusada a exclusao, a sessao e a anotacao continuam na ficha")
    void sessaoRecusadaContinuaNaFicha() throws Exception {
        mockMvc.perform(delete("/consultas/" + sessaoAnotada.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isConflict());

        mockMvc.perform(get("/pacientes/" + helena.getId() + "/ficha")
                        .header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.linhaDoTempo[?(@.consultaId == '" + sessaoAnotada.getId() + "')]")
                       .isNotEmpty())
               .andExpect(jsonPath("$.linhaDoTempo[?(@.consultaId == '" + sessaoAnotada.getId()
                       + "')].anotacoes[*].conteudo").isNotEmpty());
    }

    @Test
    @DisplayName("sessao sem anotacao continua sendo excluida com 204, como antes")
    void sessaoSemAnotacaoContinuaSendoExcluida() throws Exception {
        mockMvc.perform(delete("/consultas/" + sessaoSemAnotacao.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("anotacao ja apagada nao segura mais a sessao: excluir a anotacao libera a exclusao")
    void anotacaoApagadaLiberaAExclusao() throws Exception {
        mockMvc.perform(delete("/anotacoes/" + anotacaoDaSessao.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isNoContent());

        mockMvc.perform(delete("/consultas/" + sessaoAnotada.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isNoContent());
    }
}
