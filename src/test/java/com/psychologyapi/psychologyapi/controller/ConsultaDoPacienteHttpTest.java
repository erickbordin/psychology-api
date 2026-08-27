package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

@HttpTest
class ConsultaDoPacienteHttpTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PacienteRepository pacienteRepository;
    @Autowired
    private ConsultaRepository consultaRepository;

    private Usuario psicologo(String email) {
        return usuarioRepository.save(Fixtures.usuario(email));
    }

    private String bearerDe(Usuario usuario) {
        return "Bearer " + tokenService.gerarToken(usuario.getEmailUsuario());
    }

    @Test
    @DisplayName("lista o historico do paciente paginado, mais recente primeiro")
    void listaHistoricoPaginado() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        consultaRepository.save(Fixtures.consulta(dono, paciente,
                LocalDateTime.of(2026, 8, 12, 9, 0), Status.REALIZADA));
        consultaRepository.save(Fixtures.consulta(dono, paciente,
                LocalDateTime.of(2026, 8, 19, 9, 0), Status.REALIZADA));

        mockMvc.perform(get("/pacientes/" + paciente.getId() + "/consultas")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].dtConsulta")
                        .value(org.hamcrest.Matchers.startsWith("2026-08-19T09:00")))
                .andExpect(jsonPath("$.content[0].pacienteNome").value("Ana"))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    @DisplayName("paciente de outro psicologo responde 404")
    void pacienteDeOutroResponde404() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Usuario intruso = psicologo("intruso@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));

        mockMvc.perform(get("/pacientes/" + paciente.getId() + "/consultas")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(intruso)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("paciente inexistente responde 404")
    void pacienteInexistenteResponde404() throws Exception {
        Usuario dono = psicologo("dono@teste.com");

        mockMvc.perform(get("/pacientes/" + UUID.randomUUID() + "/consultas")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isNotFound());
    }
}
