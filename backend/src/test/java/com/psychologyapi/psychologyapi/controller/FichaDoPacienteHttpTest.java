package com.psychologyapi.psychologyapi.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.AnotacaoRepository;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.PendenciaRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * GET /pacientes/{id}/ficha — a pagina unica de spec 4 (3) numa chamada.
 *
 * A linha do tempo vem do MAIS RECENTE para o mais antigo: a ficha e aberta
 * para saber o que aconteceu na ultima sessao, nao na primeira.
 */
@HttpTest
class FichaDoPacienteHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private ConsultaRepository consultaRepository;
    @Autowired private AnotacaoRepository anotacaoRepository;
    @Autowired private PendenciaRepository pendenciaRepository;

    private String tokenDoDono;
    private String tokenDoOutro;
    private Paciente helena;
    private Paciente semNada;

    @BeforeEach
    void preparar() {
        Usuario dono = usuarioRepository.save(Fixtures.usuario("dona.ficha@example.com"));
        tokenDoDono = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());

        helena = pacienteRepository.save(Fixtures.paciente(dono, "Helena Vaz"));
        semNada = pacienteRepository.save(Fixtures.paciente(dono, "Paciente Recem Cadastrado"));

        Consulta primeira = consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-11-18T09:00:00"), Status.REALIZADA));
        Consulta segunda = consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-11-25T09:00:00"), Status.REALIZADA));
        consultaRepository.save(Fixtures.consulta(
                dono, helena, LocalDateTime.parse("2026-12-02T09:00:00"), Status.AGENDADA));

        anotacaoRepository.save(Fixtures.anotacaoDaSessao(
                dono, helena, primeira, "Primeiro encontro, mapeamos a queixa."));
        anotacaoRepository.save(Fixtures.anotacaoDaSessao(
                dono, helena, segunda, "Retomamos o combinado da semana."));
        anotacaoRepository.save(Fixtures.anotacaoDaSessao(
                dono, helena, segunda, "Complemento escrito depois da sessao."));
        // avulsa: vale, mas NAO entra na linha do tempo (spec 5.1)
        anotacaoRepository.save(Fixtures.anotacao(dono, helena, "Liguei fora do horario."));

        pendenciaRepository.save(Fixtures.pendencia(helena, "Enviar recibo de novembro"));

        Usuario outro = usuarioRepository.save(Fixtures.usuario("outro.ficha@example.com"));
        tokenDoOutro = "Bearer " + tokenService.gerarToken(outro.getEmailUsuario());
    }

    @Test
    @DisplayName("a linha do tempo traz valor e pagoEm da sessao paga")
    void linhaDoTempoTrazOsDadosDeDinheiro() throws Exception {
        Usuario dono = usuarioRepository.findByEmailUsuario("dona.ficha@example.com").orElseThrow();
        Paciente bruna = pacienteRepository.save(Fixtures.paciente(dono, "Bruna Teixeira"));
        Consulta paga = Fixtures.consulta(dono, bruna, LocalDateTime.parse("2026-12-01T09:00:00"), Status.REALIZADA);
        paga.setValor(new BigDecimal("180.00"));
        paga.setPagoEm(LocalDateTime.parse("2026-12-01T10:30:00"));
        consultaRepository.save(paga);

        mockMvc.perform(get("/pacientes/" + bruna.getId() + "/ficha")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.linhaDoTempo.length()").value(1))
               .andExpect(jsonPath("$.linhaDoTempo[0].valor").value(180.0))
               .andExpect(jsonPath("$.linhaDoTempo[0].pagoEm").value("2026-12-01T10:30:00"));
    }

    @Test
    @DisplayName("cabecalho traz os campos que a spec 4 (3) manda mostrar")
    void cabecalhoDaFicha() throws Exception {
        mockMvc.perform(get("/pacientes/" + helena.getId() + "/ficha")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.paciente.nome").value("Helena Vaz"))
               .andExpect(jsonPath("$.paciente.canalAviso").value("NENHUM"));
    }

    @Test
    @DisplayName("linha do tempo vem do encontro mais recente para o mais antigo")
    void linhaDoTempoDoMaisRecenteParaOMaisAntigo() throws Exception {
        mockMvc.perform(get("/pacientes/" + helena.getId() + "/ficha")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.linhaDoTempo.length()").value(3))
               .andExpect(jsonPath("$.linhaDoTempo[0].dtConsulta").value("2026-12-02T09:00:00"))
               .andExpect(jsonPath("$.linhaDoTempo[2].dtConsulta").value("2026-11-18T09:00:00"));
    }

    @Test
    @DisplayName("a anotacao da sessao vem DENTRO da linha daquela sessao")
    void anotacaoVemDentroDaSessao() throws Exception {
        mockMvc.perform(get("/pacientes/" + helena.getId() + "/ficha")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               // [0] e a de 02/12, ainda agendada e sem anotacao
               .andExpect(jsonPath("$.linhaDoTempo[0].anotacoes").isEmpty())
               // [1] e a de 25/11, com DUAS anotacoes. Afirmamos que as duas
               // estao la, e NAO em que ordem: `createdAt` e gravado por
               // LocalDateTime.now() no @PrePersist, e dois saves no mesmo
               // instante empatariam — teste que depende de desempate vira
               // intermitente na integracao
               .andExpect(jsonPath("$.linhaDoTempo[1].anotacoes.length()").value(2))
               .andExpect(jsonPath("$.linhaDoTempo[1].anotacoes[*].conteudo")
                       .value(hasItem("Retomamos o combinado da semana.")))
               .andExpect(jsonPath("$.linhaDoTempo[1].anotacoes[*].conteudo")
                       .value(hasItem("Complemento escrito depois da sessao.")))
               // [2] e a de 18/11, com uma
               .andExpect(jsonPath("$.linhaDoTempo[2].anotacoes.length()").value(1));
    }

    @Test
    @DisplayName("anotacao avulsa nao entra na linha do tempo")
    void avulsaNaoEntraNaLinhaDoTempo() throws Exception {
        mockMvc.perform(get("/pacientes/" + helena.getId() + "/ficha")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.linhaDoTempo[*].anotacoes[*].conteudo")
                       .value(not(hasItem("Liguei fora do horario."))));
    }

    @Test
    @DisplayName("pendencias do paciente vem junto, na mesma chamada")
    void pendenciasVemJunto() throws Exception {
        mockMvc.perform(get("/pacientes/" + helena.getId() + "/ficha")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.pendencias.length()").value(1))
               .andExpect(jsonPath("$.pendencias[0].descricao").value("Enviar recibo de novembro"));
    }

    @Test
    @DisplayName("paciente recem cadastrado abre a ficha vazia, com 200 — nao 404")
    void fichaVaziaAbre() throws Exception {
        mockMvc.perform(get("/pacientes/" + semNada.getId() + "/ficha")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.linhaDoTempo").isEmpty())
               .andExpect(jsonPath("$.pendencias").isEmpty());
    }

    @Test
    @DisplayName("ficha de paciente de outro psicologo responde 404")
    void fichaNaoVazaEntrePsicologos() throws Exception {
        mockMvc.perform(get("/pacientes/" + helena.getId() + "/ficha")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoOutro))
               .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("paciente inexistente responde 404")
    void pacienteInexistente() throws Exception {
        mockMvc.perform(get("/pacientes/" + UUID.randomUUID() + "/ficha")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isNotFound());
    }
}
