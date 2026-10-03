package com.psychologyapi.psychologyapi.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
    @Autowired private AnotacaoRepository anotacaoRepository;

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

        // 09:00 ja passou e foi marcada REALIZADA, mas ninguem escreveu nada:
        // pendencia da Fatia 2 (spec 9)
        Paciente teodoro = pacienteRepository.save(Fixtures.paciente(dono, "Teodoro Alencar"));
        consultaRepository.save(Fixtures.consulta(
                dono, teodoro, LocalDateTime.parse("2026-12-02T09:00:00"), Status.REALIZADA));

        // 07:00 tambem foi REALIZADA, e esta anotada: NAO e pendencia
        Paciente nadia = pacienteRepository.save(Fixtures.paciente(dono, "Nadia Furtado"));
        Consulta anotada = consultaRepository.save(Fixtures.consulta(
                dono, nadia, LocalDateTime.parse("2026-12-02T07:00:00"), Status.REALIZADA));
        anotacaoRepository.save(Fixtures.anotacaoDaSessao(
                dono, nadia, anotada, "Sessao registrada no mesmo dia."));

        Usuario outro = usuarioRepository.save(Fixtures.usuario("outro.painel@example.com"));
        Paciente doOutro = pacienteRepository.save(Fixtures.paciente(outro, "Paciente do outro psicologo"));
        consultaRepository.save(Fixtures.consulta(
                outro, doOutro, LocalDateTime.parse("2026-12-02T12:00:00"), Status.AGENDADA));

        Usuario semNada = usuarioRepository.save(Fixtures.usuario("dia.um@example.com"));
        tokenDeQuemNaoTemNada = "Bearer " + tokenService.gerarToken(semNada.getEmailUsuario());
    }

    private void realizadaDeBrunaHoje(Usuario dono, String pagoEm) {
        Paciente bruna = Fixtures.paciente(dono, "Bruna Teixeira");
        bruna.setValorSessao(new BigDecimal("180.00"));
        bruna = pacienteRepository.save(bruna);
        Consulta consulta = Fixtures.consulta(
                dono, bruna, LocalDateTime.parse("2026-12-02T09:30:00"), Status.REALIZADA);
        consulta.setValor(new BigDecimal("180.00"));
        consulta.setPagoEm(pagoEm == null ? null : LocalDateTime.parse(pagoEm));
        consultaRepository.save(consulta);
    }

    @Test
    @DisplayName("realizada hoje com valor e sem baixa entra como SEM_BAIXA e a linha do dia traz o valor")
    void realizadaSemBaixaEntraNaFila() throws Exception {
        realizadaDeBrunaHoje(usuarioRepository.findByEmailUsuario("dona.painel@example.com").orElseThrow(), null);

        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.pendencias[?(@.tipo == 'SEM_BAIXA')].pacienteNome").value(hasItem("Bruna Teixeira")))
               .andExpect(jsonPath("$.sessoes[?(@.pacienteNome == 'Bruna Teixeira')].valor").value(hasItem(180.0)));
    }

    @Test
    @DisplayName("realizada hoje com baixa nao gera SEM_BAIXA")
    void realizadaComBaixaNaoEPendencia() throws Exception {
        realizadaDeBrunaHoje(usuarioRepository.findByEmailUsuario("dona.painel@example.com").orElseThrow(),
                "2026-12-02T09:45:00");

        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.pendencias[?(@.tipo == 'SEM_BAIXA')].pacienteNome")
                       .value(not(hasItem("Bruna Teixeira"))));
    }

    @Test
    @DisplayName("painel devolve as sessoes de hoje em ordem de horario")
    void sessoesDoDiaEmOrdem() throws Exception {
        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isOk())
               // cinco sessoes hoje: as duas REALIZADAS tambem sao do dia e
               // continuam na linha do tempo — so CANCELADA fica de fora
               .andExpect(jsonPath("$.sessoes.length()").value(5))
               .andExpect(jsonPath("$.sessoes[0].pacienteNome").value("Nadia Furtado"))
               .andExpect(jsonPath("$.sessoes[1].pacienteNome").value("Helena Vaz"))
               .andExpect(jsonPath("$.sessoes[4].pacienteNome").value("Clara Bueno"));
    }

    @Test
    @DisplayName("proxima e a primeira sessao que ainda nao comecou")
    void proximaEAPrimeiraQueNaoComecou() throws Exception {
        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.proxima.pacienteNome").value("Rui Sampaio"));
    }

    @Test
    @DisplayName("a fila do dia tem as duas especies de pendencia, em ordem de horario")
    void pendenciaESessaoPassadaSemStatus() throws Exception {
        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.pendencias.length()").value(2))
               .andExpect(jsonPath("$.pendencias[0].pacienteNome").value("Helena Vaz"));
    }

    @Test
    @DisplayName("sessao realizada sem anotacao vira pendencia — a da Fatia 2")
    void realizadaSemAnotacaoEPendencia() throws Exception {
        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.pendencias[?(@.tipo == 'SEM_ANOTACAO')].pacienteNome")
                       .value(hasItem("Teodoro Alencar")));
    }

    @Test
    @DisplayName("sessao realizada JA anotada nao vira pendencia")
    void realizadaAnotadaNaoEPendencia() throws Exception {
        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.pendencias[*].pacienteNome")
                       .value(not(hasItem("Nadia Furtado"))));
    }

    @Test
    @DisplayName("a pendencia da Fatia 1 continua, agora marcada SEM_STATUS")
    void pendenciaDeStatusContinuaEGanhaTipo() throws Exception {
        mockMvc.perform(get("/painel/hoje").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.pendencias[?(@.tipo == 'SEM_STATUS')].pacienteNome")
                       .value(hasItem("Helena Vaz")));
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
