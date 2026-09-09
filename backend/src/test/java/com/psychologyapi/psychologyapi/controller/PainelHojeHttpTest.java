package com.psychologyapi.psychologyapi.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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

/**
 * GET /painel/hoje — a tela (1) inteira numa chamada (spec 5.6).
 *
 * O relogio e fixado num instante fixo (2026-12-02 10:00 America/Sao_Paulo)
 * via {@link RelogioFixoConfig}, para que a borda da meia-noite nunca entre
 * em jogo e o teste seja deterministico em qualquer hora do dia em que rodar.
 */
@HttpTest
@Import(PainelHojeHttpTest.RelogioFixoConfig.class)
class PainelHojeHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private ConsultaRepository consultaRepository;

    private String tokenDoDono;
    private String tokenDeQuemNaoTemNada;

    @BeforeEach
    void preparar() {
        Usuario dono = usuarioRepository.save(Fixtures.usuario("dona.painel@example.com"));
        tokenDoDono = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());

        Paciente helena = pacienteRepository.save(Fixtures.paciente(dono, "Helena Vaz"));
        Paciente rui = pacienteRepository.save(Fixtures.paciente(dono, "Rui Sampaio"));
        Paciente clara = pacienteRepository.save(Fixtures.paciente(dono, "Clara Bueno"));

        // "agora" fixado em 2026-12-02T10:00 (ver RelogioFixoConfig): 08:00 ja
        // passou (pendencia), 11:00 e 15:00 ainda estao por vir
        consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-12-02T08:00:00"), Status.AGENDADA));
        consultaRepository.save(Fixtures.consulta(
                dono, rui, LocalDateTime.parse("2026-12-02T11:00:00"), Status.AGENDADA));
        consultaRepository.save(Fixtures.consulta(
                dono, clara, LocalDateTime.parse("2026-12-02T15:00:00"), Status.AGENDADA));

        Usuario outro = usuarioRepository.save(Fixtures.usuario("outro.painel@example.com"));
        Paciente doOutro = pacienteRepository.save(Fixtures.paciente(outro, "Paciente do outro psicologo"));
        consultaRepository.save(Fixtures.consulta(
                outro, doOutro, LocalDateTime.parse("2026-12-02T12:00:00"), Status.AGENDADA));

        Usuario semNada = usuarioRepository.save(Fixtures.usuario("dia.um@example.com"));
        tokenDeQuemNaoTemNada = "Bearer " + tokenService.gerarToken(semNada.getEmailUsuario());
    }

    @Test
    @DisplayName("painel devolve as sessoes de hoje em ordem de horario")
    void sessoesDoDiaEmOrdem() throws Exception {
        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.sessoes.length()").value(3))
               .andExpect(jsonPath("$.sessoes[0].pacienteNome").value("Helena Vaz"))
               .andExpect(jsonPath("$.sessoes[2].pacienteNome").value("Clara Bueno"));
    }

    @Test
    @DisplayName("proxima e a primeira sessao que ainda nao comecou")
    void proximaEAPrimeiraQueNaoComecou() throws Exception {
        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.proxima.pacienteNome").value("Rui Sampaio"));
    }

    @Test
    @DisplayName("pendencia e sessao que passou da hora e continua agendada")
    void pendenciaESessaoPassadaSemStatus() throws Exception {
        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.pendencias.length()").value(1))
               .andExpect(jsonPath("$.pendencias[0].pacienteNome").value("Helena Vaz"));
    }

    @Test
    @DisplayName("agenda de outro psicologo nao aparece no painel")
    void painelNaoVazaEntrePsicologos() throws Exception {
        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.sessoes[*].pacienteNome")
                       .value(not(hasItem("Paciente do outro psicologo"))));
    }

    @Test
    @DisplayName("dia 1 do psicologo: tudo vazio, proxima nula, e 200 — nao 404")
    void diaUmRespondeVazio() throws Exception {
        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDeQuemNaoTemNada))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.proxima").doesNotExist())
               .andExpect(jsonPath("$.sessoes").isEmpty())
               .andExpect(jsonPath("$.pendencias").isEmpty());
    }

    /** Substitui o Clock de producao por um fixo em 2026-12-02T10:00
     *  America/Sao_Paulo, para que "hoje" e "agora" nao dependam da hora em
     *  que a suite roda. Nome de bean diferente do "clock" de ClockConfig —
     *  bean overriding e desligado por padrao no Spring Boot — e @Primary
     *  resolve a ambiguidade de tipo na injecao a favor deste. */
    @TestConfiguration
    static class RelogioFixoConfig {

        @Bean
        @Primary
        Clock relogioFixo() {
            return Clock.fixed(
                    LocalDateTime.parse("2026-12-02T10:00:00")
                            .atZone(ZoneId.of("America/Sao_Paulo"))
                            .toInstant(),
                    ZoneId.of("America/Sao_Paulo"));
        }
    }
}
