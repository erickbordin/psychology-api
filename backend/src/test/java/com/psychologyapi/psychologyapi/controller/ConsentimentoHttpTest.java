package com.psychologyapi.psychologyapi.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psychologyapi.psychologyapi.model.CanalAviso;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

@HttpTest
class ConsentimentoHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void preparar() {
        Usuario dono = usuarioRepository.save(Fixtures.usuario("dona.consentimento@example.com"));
        token = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());
    }

    private JsonNode cadastrar(String corpo) throws Exception {
        String json = mockMvc.perform(post("/pacientes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private JsonNode atualizar(String id, String corpo) throws Exception {
        String json = mockMvc.perform(put("/pacientes/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private JsonNode buscar(String id) throws Exception {
        String json = mockMvc.perform(get("/pacientes/" + id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private int linhasDeTrilha(String id) throws Exception {
        String json = mockMvc.perform(get("/auditoria")
                        .param("entidade", "ConsentimentoDoPaciente")
                        .param("entidadeId", id)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("content").size();
    }

    @Test
    @DisplayName("cadastro com canal EMAIL grava a data de consentimento e a trilha")
    void cadastroComEmailGravaConsentimento() throws Exception {
        JsonNode criado = cadastrar("""
                {"nome":"Helena Vaz","email":"helena@example.com","canalAviso":"EMAIL"}
                """);

        assertNotNull(criado.get("consentimentoAvisoEm"));
        assertEquals(false, criado.get("consentimentoAvisoEm").isNull());
        assertEquals(1, linhasDeTrilha(criado.get("idPaciente").asText()));
    }

    @Test
    @DisplayName("cadastro com canal NENHUM nao grava consentimento nem trilha")
    void cadastroSemEmailNaoGravaConsentimento() throws Exception {
        JsonNode criado = cadastrar("""
                {"nome":"Helena Vaz","canalAviso":"NENHUM"}
                """);

        assertEquals(true, criado.get("consentimentoAvisoEm") == null || criado.get("consentimentoAvisoEm").isNull());
        assertEquals(0, linhasDeTrilha(criado.get("idPaciente").asText()));
    }

    @Test
    @DisplayName("PUT de NENHUM para EMAIL grava a data e a trilha")
    void ligarGravaConsentimento() throws Exception {
        String id = cadastrar("""
                {"nome":"Helena Vaz","email":"helena@example.com","canalAviso":"NENHUM"}
                """).get("idPaciente").asText();

        atualizar(id, "{\"canalAviso\":\"EMAIL\"}");

        assertEquals(false, buscar(id).get("consentimentoAvisoEm") == null
                || buscar(id).get("consentimentoAvisoEm").isNull());
        assertEquals(1, linhasDeTrilha(id));
    }

    @Test
    @DisplayName("PUT mantendo EMAIL nao muda a data nem gera trilha")
    void manterNaoMudaConsentimento() throws Exception {
        String id = cadastrar("""
                {"nome":"Helena Vaz","email":"helena@example.com","canalAviso":"EMAIL"}
                """).get("idPaciente").asText();
        String antes = buscar(id).get("consentimentoAvisoEm").asText();

        atualizar(id, "{\"canalAviso\":\"EMAIL\",\"nome\":\"Helena Vaz Souza\"}");

        assertEquals(antes, buscar(id).get("consentimentoAvisoEm").asText());
        assertEquals(1, linhasDeTrilha(id));
    }

    @Test
    @DisplayName("PUT para NENHUM limpa o consentimento e grava trilha")
    void desligarLimpaConsentimento() throws Exception {
        String id = cadastrar("""
                {"nome":"Helena Vaz","email":"helena@example.com","canalAviso":"EMAIL"}
                """).get("idPaciente").asText();

        atualizar(id, "{\"canalAviso\":\"NENHUM\"}");

        JsonNode depois = buscar(id);
        assertEquals(true, depois.get("consentimentoAvisoEm") == null || depois.get("consentimentoAvisoEm").isNull());
        assertEquals(2, linhasDeTrilha(id));
    }

    @Test
    @DisplayName("PUT so com o nome nao toca o consentimento")
    void putSoComNomeNaoTocaConsentimento() throws Exception {
        String id = cadastrar("""
                {"nome":"Helena Vaz","email":"helena@example.com","canalAviso":"EMAIL"}
                """).get("idPaciente").asText();
        String antes = buscar(id).get("consentimentoAvisoEm").asText();

        JsonNode resposta = atualizar(id, "{\"nome\":\"Helena Vaz Souza\"}");

        assertEquals("EMAIL", resposta.get("canalAviso").asText());
        assertEquals(antes, resposta.get("consentimentoAvisoEm").asText());
        assertEquals(1, linhasDeTrilha(id));
    }

    @Test
    @DisplayName("descadastradoEm aparece no response quando preenchido")
    void descadastradoEmApareceNoResponse() throws Exception {
        Usuario dono = usuarioRepository.findByEmailUsuario("dona.consentimento@example.com").orElseThrow();
        Paciente paciente = Fixtures.paciente(dono, "Bruna Teixeira");
        paciente.setDescadastradoEm(LocalDateTime.parse("2026-10-01T08:30:00"));
        paciente = pacienteRepository.save(paciente);

        mockMvc.perform(get("/pacientes/" + paciente.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descadastradoEm").value("2026-10-01T08:30:00"));
    }

    @Test
    @DisplayName("religar EMAIL depois do descadastro grava nova data e preserva descadastradoEm")
    void recadastroPreservaHistorico() throws Exception {
        Usuario dono = usuarioRepository.findByEmailUsuario("dona.consentimento@example.com").orElseThrow();
        Paciente paciente = Fixtures.paciente(dono, "Bruna Teixeira");
        paciente.setEmailPaciente("bruna@example.com");
        paciente.setCanalAviso(CanalAviso.NENHUM);
        paciente.setDescadastradoEm(LocalDateTime.parse("2026-10-01T08:30:00"));
        UUID id = pacienteRepository.save(paciente).getId();

        JsonNode resposta = atualizar(id.toString(), "{\"canalAviso\":\"EMAIL\"}");

        assertEquals(false, resposta.get("consentimentoAvisoEm") == null || resposta.get("consentimentoAvisoEm").isNull());
        assertNotEquals("", resposta.get("consentimentoAvisoEm").asText());
        assertEquals("2026-10-01T08:30:00", resposta.get("descadastradoEm").asText());
        assertEquals(1, linhasDeTrilha(id.toString()));
    }

    @Test
    @DisplayName("paciente legado com EMAIL sem consentimento grava a data ao marcar a caixa")
    void legadoComEmailGravaConsentimentoAoMarcar() throws Exception {
        UUID id = legadoComEmailSemConsentimento();

        JsonNode resposta = atualizar(id.toString(), "{\"canalAviso\":\"EMAIL\"}");

        assertEquals(false, resposta.get("consentimentoAvisoEm") == null || resposta.get("consentimentoAvisoEm").isNull());
        assertEquals(1, linhasDeTrilha(id.toString()));
    }

    @Test
    @DisplayName("paciente legado com EMAIL sem consentimento segue sem consentimento num PUT so com o nome")
    void legadoComEmailNaoGanhaConsentimentoSemCaixa() throws Exception {
        UUID id = legadoComEmailSemConsentimento();

        JsonNode resposta = atualizar(id.toString(), "{\"nome\":\"Bruna Teixeira Lima\"}");

        assertEquals(true, resposta.get("consentimentoAvisoEm") == null || resposta.get("consentimentoAvisoEm").isNull());
        assertEquals(0, linhasDeTrilha(id.toString()));
    }

    private UUID legadoComEmailSemConsentimento() {
        Usuario dono = usuarioRepository.findByEmailUsuario("dona.consentimento@example.com").orElseThrow();
        Paciente paciente = Fixtures.paciente(dono, "Bruna Teixeira");
        paciente.setEmailPaciente("bruna@example.com");
        paciente.setCanalAviso(CanalAviso.EMAIL);
        paciente.setConsentimentoAvisoEm(null);
        return pacienteRepository.save(paciente).getId();
    }
}
