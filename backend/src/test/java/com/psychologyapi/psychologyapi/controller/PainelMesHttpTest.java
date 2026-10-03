package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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

@HttpTest
@Import(PainelHojeHttpTest.RelogioFixoConfig.class)
class PainelMesHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private ConsultaRepository consultaRepository;

    private String tokenDoDono;
    private String tokenDeQuemNaoTemNada;
    private String tokenDeQuemSoCancelou;

    private Consulta salvar(Usuario dono, Paciente paciente, String quando, Status status) {
        return consultaRepository.save(
                Fixtures.consulta(dono, paciente, LocalDateTime.parse(quando), status));
    }

    @BeforeEach
    void preparar() {
        Usuario dono = usuarioRepository.save(Fixtures.usuario("dona.painel.mes@example.com"));
        tokenDoDono = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());

        Paciente bruna = pacienteRepository.save(Fixtures.paciente(dono, "Bruna Teixeira"));
        salvar(dono, bruna, "2026-12-01T09:00:00", Status.REALIZADA);
        salvar(dono, bruna, "2026-12-08T09:00:00", Status.REALIZADA);
        salvar(dono, bruna, "2026-12-15T09:00:00", Status.FALTOU);
        salvar(dono, bruna, "2026-11-10T09:00:00", Status.REALIZADA);

        Paciente ivan = pacienteRepository.save(Fixtures.paciente(dono, "Ivan Prado"));
        salvar(dono, ivan, "2026-12-05T09:00:00", Status.REALIZADA);
        salvar(dono, ivan, "2026-12-12T09:00:00", Status.CANCELADA);

        Consulta excluida = salvar(dono, ivan, "2026-12-20T09:00:00", Status.REALIZADA);
        excluida.setDeletedAt(LocalDateTime.parse("2026-12-21T09:00:00"));
        consultaRepository.save(excluida);

        Usuario outro = usuarioRepository.save(Fixtures.usuario("outro.painel.mes@example.com"));
        Paciente doOutro = pacienteRepository.save(Fixtures.paciente(outro, "Paciente do outro psicologo"));
        salvar(outro, doOutro, "2026-12-09T09:00:00", Status.REALIZADA);

        Usuario semNada = usuarioRepository.save(Fixtures.usuario("sem.nada.painel.mes@example.com"));
        tokenDeQuemNaoTemNada = "Bearer " + tokenService.gerarToken(semNada.getEmailUsuario());

        Usuario soCancelou = usuarioRepository.save(Fixtures.usuario("so.cancelou.painel.mes@example.com"));
        tokenDeQuemSoCancelou = "Bearer " + tokenService.gerarToken(soCancelou.getEmailUsuario());
        Paciente clara = pacienteRepository.save(Fixtures.paciente(soCancelou, "Clara Bueno"));
        salvar(soCancelou, clara, "2026-12-03T09:00:00", Status.CANCELADA);
    }

    @Test
    @DisplayName("o mes conta realizadas, faltas, canceladas e pacientes ativos, e a taxa de falta sobre realizadas mais faltas")
    void contagemDoMes() throws Exception {
        mockMvc.perform(get("/painel/mes?ano=2026&mes=12").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.realizadas").value(3))
               .andExpect(jsonPath("$.faltas").value(1))
               .andExpect(jsonPath("$.canceladas").value(1))
               .andExpect(jsonPath("$.pacientesAtivos").value(2))
               .andExpect(jsonPath("$.taxaDeFalta").value(0.25));
    }

    @Test
    @DisplayName("quem nao tem nada recebe zeros e taxa de falta nula")
    void semNada() throws Exception {
        mockMvc.perform(get("/painel/mes?ano=2026&mes=12")
                       .header(HttpHeaders.AUTHORIZATION, tokenDeQuemNaoTemNada))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.realizadas").value(0))
               .andExpect(jsonPath("$.taxaDeFalta").isEmpty())
               .andExpect(jsonPath("$.pacientesAtivos").value(0));
    }

    @Test
    @DisplayName("mes so com cancelada nao tem denominador: taxa de falta nula")
    void soCancelada() throws Exception {
        mockMvc.perform(get("/painel/mes?ano=2026&mes=12")
                       .header(HttpHeaders.AUTHORIZATION, tokenDeQuemSoCancelou))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.canceladas").value(1))
               .andExpect(jsonPath("$.taxaDeFalta").isEmpty());
    }

    @Test
    @DisplayName("sem ano nem mes, devolve o mes corrente do relogio")
    void semParametros() throws Exception {
        mockMvc.perform(get("/painel/mes").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.ano").value(2026))
               .andExpect(jsonPath("$.mes").value(12));
    }

    @Test
    @DisplayName("so o mes: 400 apontando o campo ano")
    void soMes() throws Exception {
        mockMvc.perform(get("/painel/mes?mes=12").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[0].campo").value("ano"));
    }
}
