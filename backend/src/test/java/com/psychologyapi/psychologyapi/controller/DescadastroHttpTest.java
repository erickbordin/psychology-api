package com.psychologyapi.psychologyapi.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

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
class DescadastroHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private ObjectMapper objectMapper;

    private String tokenDoDono;
    private Paciente paciente;

    @BeforeEach
    void preparar() {
        Usuario dono = usuarioRepository.save(Fixtures.usuario("dona.descadastro@example.com"));
        tokenDoDono = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());

        Paciente novo = Fixtures.paciente(dono, "Helena Vaz");
        novo.setEmailPaciente("helena.vaz@example.com");
        novo.setCanalAviso(CanalAviso.EMAIL);
        novo.setConsentimentoAvisoEm(LocalDateTime.parse("2026-09-20T10:00:00"));
        paciente = pacienteRepository.save(novo);
    }

    private int linhasDeTrilha() throws Exception {
        String json = mockMvc.perform(get("/auditoria")
                        .param("entidade", "DescadastroDoPaciente")
                        .param("entidadeId", paciente.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("content").size();
    }

    @Test
    @DisplayName("GET sem token de paciente autorizado devolve so ativo=true, sem nome nem e-mail")
    void consultarAtivo() throws Exception {
        mockMvc.perform(get("/descadastro/" + paciente.getTokenDescadastro()))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"ativo\":true}", true))
                .andExpect(content().string(not(containsString("Helena"))))
                .andExpect(content().string(not(containsString("helena.vaz@example.com"))));
    }

    @Test
    @DisplayName("POST descadastra, limpa o consentimento e grava a trilha do psicologo")
    void descadastrar() throws Exception {
        mockMvc.perform(post("/descadastro/" + paciente.getTokenDescadastro()))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"ativo\":false}", true));

        Paciente depois = pacienteRepository.findById(paciente.getId()).orElseThrow();
        assertEquals(CanalAviso.NENHUM, depois.getCanalAviso());
        assertNull(depois.getConsentimentoAvisoEm());
        assertNotNull(depois.getDescadastradoEm());
        assertEquals(1, linhasDeTrilha());

        mockMvc.perform(get("/descadastro/" + paciente.getTokenDescadastro()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    @DisplayName("segundo POST e idempotente: 200, sem nova trilha e sem sobrescrever a data")
    void descadastrarDuasVezes() throws Exception {
        mockMvc.perform(post("/descadastro/" + paciente.getTokenDescadastro())).andExpect(status().isOk());
        LocalDateTime primeira = pacienteRepository.findById(paciente.getId()).orElseThrow().getDescadastradoEm();

        mockMvc.perform(post("/descadastro/" + paciente.getTokenDescadastro()))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"ativo\":false}", true));

        assertEquals(primeira, pacienteRepository.findById(paciente.getId()).orElseThrow().getDescadastradoEm());
        assertEquals(1, linhasDeTrilha());
    }

    @Test
    @DisplayName("token desconhecido responde 404 no envelope padrao")
    void tokenDesconhecido() throws Exception {
        mockMvc.perform(get("/descadastro/token-que-nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensagem").value("Link invalido"));
        mockMvc.perform(post("/descadastro/token-que-nao-existe"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("token de paciente excluido responde 404")
    void tokenDePacienteExcluido() throws Exception {
        paciente.setDeletedAt(LocalDateTime.parse("2026-10-02T09:00:00"));
        pacienteRepository.save(paciente);

        mockMvc.perform(get("/descadastro/" + paciente.getTokenDescadastro()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Link invalido"));
        mockMvc.perform(post("/descadastro/" + paciente.getTokenDescadastro()))
                .andExpect(status().isNotFound());
    }
}
