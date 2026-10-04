package com.psychologyapi.psychologyapi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psychologyapi.psychologyapi.model.Anotacao;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Pendencia;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.AnotacaoRepository;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.LogAuditoriaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.PendenciaRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

@HttpTest
class TrilhaLegivelHttpTest {

    private static final Set<String> COM_PACIENTE =
            Set.of("Paciente", "FichaDoPaciente", "Consulta", "Anotacao", "Pendencia");

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private ConsultaRepository consultaRepository;
    @Autowired private AnotacaoRepository anotacaoRepository;
    @Autowired private PendenciaRepository pendenciaRepository;
    @Autowired private LogAuditoriaRepository logAuditoriaRepository;
    @Autowired private ObjectMapper objectMapper;

    private Usuario dona;
    private String token;

    @BeforeEach
    void preparar() {
        dona = usuarioRepository.save(Fixtures.usuario("dona.trilha@example.com"));
        token = "Bearer " + tokenService.gerarToken(dona.getEmailUsuario());
    }

    private JsonNode enviar(MockHttpServletRequestBuilder requisicao,
            int esperado) throws Exception {
        String json = mockMvc.perform(requisicao.header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(esperado))
                .andReturn().getResponse().getContentAsString();
        return json.isEmpty() ? null : objectMapper.readTree(json);
    }

    private String cadastrarPaciente() throws Exception {
        return enviar(post("/pacientes").content("{\"nome\":\"Helena Vaz\"}"), 201).get("idPaciente").asText();
    }

    private String agendarSessao(String pacienteId) throws Exception {
        String quando = LocalDate.now().plusDays(3).atTime(10, 0) + ":00";
        return enviar(post("/consultas")
                .content("{\"pacienteId\":\"" + pacienteId + "\",\"dtConsulta\":\"" + quando + "\"}"), 201)
                .get("id").asText();
    }

    private JsonNode trilha(String consulta) throws Exception {
        String json = mockMvc.perform(get("/auditoria" + consulta).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("content");
    }

    private List<JsonNode> linhasDe(JsonNode conteudo, String entidade) {
        List<JsonNode> linhas = new ArrayList<>();
        conteudo.forEach(linha -> {
            if (entidade.equals(linha.get("entidade").asText())) {
                linhas.add(linha);
            }
        });
        return linhas;
    }

    private boolean nulo(JsonNode linha, String campo) {
        return linha.has(campo) && linha.get(campo).isNull();
    }

    @Test
    @DisplayName("a trilha resolve o paciente de cada tipo de linha: paciente, ficha, sessao, anotacao e pendencia")
    void pacienteResolvidoPorTipo() throws Exception {
        String pacienteId = cadastrarPaciente();
        enviar(get("/pacientes/" + pacienteId + "/ficha"), 200);
        String consultaId = agendarSessao(pacienteId);
        enviar(post("/pacientes/" + pacienteId + "/anotacoes")
                .content("{\"anotacao\":\"Primeira conversa\",\"consultaId\":\"" + consultaId + "\"}"), 201);
        enviar(post("/pacientes/" + pacienteId + "/pendencias").content("{\"descricao\":\"Pedir laudo\"}"), 201);

        JsonNode conteudo = trilha("?size=50");

        for (String entidade : COM_PACIENTE) {
            List<JsonNode> linhas = linhasDe(conteudo, entidade);
            assertFalse(linhas.isEmpty(), "sem linha de " + entidade);
            for (JsonNode linha : linhas) {
                assertEquals(pacienteId, linha.get("pacienteId").asText(), entidade);
                assertEquals("Helena Vaz", linha.get("pacienteNome").asText(), entidade);
                assertEquals(false, linha.get("pacienteExcluido").asBoolean(), entidade);
            }
        }
    }

    @Test
    @DisplayName("a trilha resolve o paciente nas linhas de consentimento e de descadastro")
    void consentimentoEDescadastroResolvem() throws Exception {
        String pacienteId = enviar(post("/pacientes")
                .content("{\"nome\":\"Helena Vaz\",\"email\":\"helena.trilha@example.com\",\"canalAviso\":\"EMAIL\"}"),
                201).get("idPaciente").asText();
        String tokenDescadastro = pacienteRepository.findById(UUID.fromString(pacienteId)).orElseThrow()
                .getTokenDescadastro();
        mockMvc.perform(post("/descadastro/" + tokenDescadastro)).andExpect(status().isOk());

        JsonNode conteudo = trilha("?size=50");

        for (String entidade : List.of("ConsentimentoDoPaciente", "DescadastroDoPaciente")) {
            List<JsonNode> linhas = linhasDe(conteudo, entidade);
            assertFalse(linhas.isEmpty(), "sem linha de " + entidade);
            for (JsonNode linha : linhas) {
                assertEquals(pacienteId, linha.get("pacienteId").asText(), entidade);
                assertEquals("Helena Vaz", linha.get("pacienteNome").asText(), entidade);
                assertEquals(false, linha.get("pacienteExcluido").asBoolean(), entidade);
            }
        }
    }

    @Test
    @DisplayName("a linha de ajustes vem sem paciente")
    void ajustesSemPaciente() throws Exception {
        enviar(put("/ajustes").content("{\"nome\":\"Dona Trilha\",\"antecedencia\":\"H24\"}"), 200);

        List<JsonNode> linhas = linhasDe(trilha("?size=50"), "Ajustes");

        assertFalse(linhas.isEmpty());
        for (JsonNode linha : linhas) {
            assertTrue(nulo(linha, "pacienteId"));
            assertTrue(nulo(linha, "pacienteNome"));
            assertTrue(nulo(linha, "pacienteExcluido"));
        }
    }

    @Test
    @DisplayName("paciente excluido segue com o nome e vem marcado como excluido")
    void pacienteExcluidoSegueComNome() throws Exception {
        String pacienteId = cadastrarPaciente();
        agendarSessao(pacienteId);
        enviar(delete("/pacientes/" + pacienteId), 204);

        List<JsonNode> linhas = linhasDe(trilha("?size=50"), "Paciente");

        assertEquals(2, linhas.size());
        for (JsonNode linha : linhas) {
            assertEquals(pacienteId, linha.get("pacienteId").asText());
            assertEquals("Helena Vaz", linha.get("pacienteNome").asText());
            assertEquals(true, linha.get("pacienteExcluido").asBoolean());
        }
        List<JsonNode> sessoes = linhasDe(trilha("?size=50"), "Consulta");
        assertFalse(sessoes.isEmpty());
        for (JsonNode linha : sessoes) {
            assertEquals(true, linha.get("pacienteExcluido").asBoolean());
        }
    }

    @Test
    @DisplayName("sessao excluida ainda resolve o paciente na linha de exclusao")
    void sessaoExcluidaResolve() throws Exception {
        String pacienteId = cadastrarPaciente();
        String consultaId = agendarSessao(pacienteId);
        enviar(delete("/consultas/" + consultaId), 204);

        List<JsonNode> exclusoes = linhasDe(trilha("?size=50"), "Consulta").stream()
                .filter(linha -> AcaoAuditoria.EXCLUSAO.name().equals(linha.get("acao").asText()))
                .toList();

        assertEquals(1, exclusoes.size());
        assertEquals(pacienteId, exclusoes.get(0).get("pacienteId").asText());
        assertEquals("Helena Vaz", exclusoes.get(0).get("pacienteNome").asText());
        assertEquals(false, exclusoes.get(0).get("pacienteExcluido").asBoolean());
    }

    @Test
    @DisplayName("anotacao e pendencia excluidas ainda resolvem o paciente")
    void anotacaoEPendenciaExcluidasResolvem() throws Exception {
        String pacienteId = cadastrarPaciente();
        String anotacaoId = enviar(post("/pacientes/" + pacienteId + "/anotacoes")
                .content("{\"anotacao\":\"Texto\"}"), 201).get("id").asText();
        String pendenciaId = enviar(post("/pacientes/" + pacienteId + "/pendencias")
                .content("{\"descricao\":\"Pedir laudo\"}"), 201).get("id").asText();
        enviar(delete("/anotacoes/" + anotacaoId), 204);
        enviar(delete("/pendencias/" + pendenciaId), 204);

        JsonNode conteudo = trilha("?size=50");

        for (String entidade : List.of("Anotacao", "Pendencia")) {
            for (JsonNode linha : linhasDe(conteudo, entidade)) {
                assertEquals(pacienteId, linha.get("pacienteId").asText(), entidade);
                assertEquals("Helena Vaz", linha.get("pacienteNome").asText(), entidade);
            }
        }
    }

    @Test
    @DisplayName("linha da trilha que aponta para dado de outro psicologo nao resolve paciente")
    void naoResolvePacienteAlheio() throws Exception {
        Usuario outro = usuarioRepository.save(Fixtures.usuario("outra.trilha@example.com"));
        Paciente alheio = pacienteRepository.save(Fixtures.paciente(outro, "Paciente Alheio"));
        Consulta consultaAlheia = consultaRepository.save(
                Fixtures.consulta(outro, alheio, LocalDateTime.now().plusDays(2), Status.AGENDADA));
        Anotacao anotacaoAlheia = anotacaoRepository.save(Fixtures.anotacao(outro, alheio, "Texto alheio"));
        Pendencia pendenciaAlheia = pendenciaRepository.save(Fixtures.pendencia(alheio, "Pendencia alheia"));
        logAuditoriaRepository.save(Fixtures.logAuditoria(dona, AcaoAuditoria.VISUALIZACAO, "Paciente",
                alheio.getId()));
        logAuditoriaRepository.save(Fixtures.logAuditoria(dona, AcaoAuditoria.VISUALIZACAO, "FichaDoPaciente",
                alheio.getId()));
        logAuditoriaRepository.save(Fixtures.logAuditoria(dona, AcaoAuditoria.VISUALIZACAO, "Consulta",
                consultaAlheia.getId()));
        logAuditoriaRepository.save(Fixtures.logAuditoria(dona, AcaoAuditoria.VISUALIZACAO, "Anotacao",
                anotacaoAlheia.getId()));
        logAuditoriaRepository.save(Fixtures.logAuditoria(dona, AcaoAuditoria.VISUALIZACAO, "Pendencia",
                pendenciaAlheia.getId()));
        logAuditoriaRepository.save(Fixtures.logAuditoria(dona, AcaoAuditoria.ATUALIZACAO,
                "ConsentimentoDoPaciente", alheio.getId()));
        logAuditoriaRepository.save(Fixtures.logAuditoria(dona, AcaoAuditoria.ATUALIZACAO,
                "DescadastroDoPaciente", alheio.getId()));

        JsonNode conteudo = trilha("?size=50");

        assertEquals(7, conteudo.size());
        for (JsonNode linha : conteudo) {
            String entidade = linha.get("entidade").asText();
            assertTrue(nulo(linha, "pacienteId"), entidade);
            assertTrue(nulo(linha, "pacienteNome"), entidade);
            assertTrue(nulo(linha, "pacienteExcluido"), entidade);
        }
    }

    @Test
    @DisplayName("o filtro por entidade continua e traz as linhas ja resolvidas")
    void filtroPorEntidadeContinua() throws Exception {
        String pacienteId = cadastrarPaciente();
        agendarSessao(pacienteId);

        JsonNode conteudo = trilha("?entidade=Consulta");

        assertEquals(1, conteudo.size());
        for (JsonNode linha : conteudo) {
            assertEquals("Consulta", linha.get("entidade").asText());
            assertEquals(pacienteId, linha.get("pacienteId").asText());
            assertEquals("Helena Vaz", linha.get("pacienteNome").asText());
        }
    }

    @Test
    @DisplayName("ler a trilha nao grava linha de auditoria")
    void leituraNaoAudita() throws Exception {
        cadastrarPaciente();
        long antes = logAuditoriaRepository.count();

        trilha("");
        trilha("?size=50");

        assertEquals(antes, logAuditoriaRepository.count());
    }
}
