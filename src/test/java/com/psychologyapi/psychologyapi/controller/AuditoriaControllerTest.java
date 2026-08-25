package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.LogAuditoriaRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

@HttpTest
class AuditoriaControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private LogAuditoriaRepository logAuditoriaRepository;

    private Usuario psicologo(String email) {
        return usuarioRepository.save(Fixtures.usuario(email));
    }

    private String bearerDe(Usuario usuario) {
        return "Bearer " + tokenService.gerarToken(usuario.getEmailUsuario());
    }

    private void logDe(Usuario dono, String entidade) {
        logAuditoriaRepository
                .save(Fixtures.logAuditoria(dono, AcaoAuditoria.VISUALIZACAO, entidade, UUID.randomUUID()));
    }

    @Test
    @DisplayName("GET /auditoria devolve 200 e o envelope de PagedModel")
    void listaComOEnvelopeDePagedModel() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        logDe(dono, "Paciente");
        logDe(dono, "Consulta");

        mockMvc.perform(get("/auditoria").header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].acao").value("VISUALIZACAO"));
    }

    @Test
    @DisplayName("GET /auditoria respeita o size da query string")
    void respeitaOSizeDaQueryString() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        logDe(dono, "Paciente");
        logDe(dono, "Consulta");

        mockMvc.perform(get("/auditoria").param("size", "1").header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    @DisplayName("GET /auditoria nao devolve a trilha de outro psicologo")
    void naoVazaTrilhaDeOutroPsicologo() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Usuario intruso = psicologo("intruso@teste.com");
        logDe(dono, "Paciente");

        mockMvc.perform(get("/auditoria").header(HttpHeaders.AUTHORIZATION, bearerDe(intruso)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /auditoria nao registra log da propria leitura")
    void leituraNaoSeAudita() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        logDe(dono, "Paciente");

        mockMvc.perform(get("/auditoria").header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/auditoria").header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }
}
