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
import com.psychologyapi.psychologyapi.model.entity.LogAuditoria;
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
        logDe(dono, entidade, UUID.randomUUID());
    }

    private LogAuditoria logDe(Usuario dono, String entidade, UUID entidadeId) {
        return logAuditoriaRepository
                .save(Fixtures.logAuditoria(dono, AcaoAuditoria.VISUALIZACAO, entidade, entidadeId));
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

    @Test
    @DisplayName("GET /auditoria?entidade= devolve so a trilha daquele tipo, e nao a de outro psicologo")
    void filtraPorEntidade() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Usuario intruso = psicologo("intruso@teste.com");
        logDe(dono, "Paciente");
        logDe(dono, "Paciente");
        logDe(dono, "Consulta");
        logDe(intruso, "Paciente");

        mockMvc.perform(get("/auditoria").param("entidade", "Paciente")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].entidade").value("Paciente"))
                .andExpect(jsonPath("$.content[1].entidade").value("Paciente"));
    }

    @Test
    @DisplayName("GET /auditoria?entidade=&entidadeId= devolve so a historia daquele registro")
    void filtraPorEntidadeEEntidadeId() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        UUID idDoPaciente = UUID.randomUUID();
        logDe(dono, "Paciente", idDoPaciente);
        logDe(dono, "Paciente", idDoPaciente);
        // mesmo id, outra entidade: nao pode aparecer
        logDe(dono, "Consulta", idDoPaciente);
        // outro paciente do mesmo dono: nao pode aparecer
        logDe(dono, "Paciente", UUID.randomUUID());

        mockMvc.perform(get("/auditoria")
                .param("entidade", "Paciente")
                .param("entidadeId", idDoPaciente.toString())
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].entidadeId").value(idDoPaciente.toString()))
                .andExpect(jsonPath("$.content[1].entidadeId").value(idDoPaciente.toString()));
    }

    @Test
    @DisplayName("GET /auditoria?entidade=&entidadeId= respeita a paginacao")
    void filtraPorEntidadeEEntidadeIdRespeitaAPaginacao() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        UUID idDoPaciente = UUID.randomUUID();
        logDe(dono, "Paciente", idDoPaciente);
        logDe(dono, "Paciente", idDoPaciente);
        logDe(dono, "Paciente", idDoPaciente);

        mockMvc.perform(get("/auditoria")
                .param("entidade", "Paciente")
                .param("entidadeId", idDoPaciente.toString())
                .param("size", "2")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    @DisplayName("GET /auditoria nao devolve a historia de um registro de outro psicologo mesmo filtrando por entidade e entidadeId")
    void naoVazaHistoriaDeRegistroDeOutroPsicologo() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Usuario intruso = psicologo("intruso@teste.com");
        UUID idDoPaciente = UUID.randomUUID();
        logDe(dono, "Paciente", idDoPaciente);

        mockMvc.perform(get("/auditoria")
                .param("entidade", "Paciente")
                .param("entidadeId", idDoPaciente.toString())
                .header(HttpHeaders.AUTHORIZATION, bearerDe(intruso)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("GET /auditoria?entidadeId= sem entidade devolve 400")
    void entidadeIdSemEntidadeDevolve400() throws Exception {
        Usuario dono = psicologo("dono@teste.com");

        mockMvc.perform(get("/auditoria")
                .param("entidadeId", UUID.randomUUID().toString())
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros").isArray())
                .andExpect(jsonPath("$.erros.length()").value(1))
                .andExpect(jsonPath("$.erros[0].campo").value("entidadeId"));
    }
}
