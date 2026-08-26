package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * GET /pacientes: a tela mais obvia do produto (lista de pacientes do
 * psicologo logado) precisa funcionar sem filtro nenhum, e continuar
 * filtrando por nome quando ele for informado.
 */
@HttpTest
class PacienteControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PacienteRepository pacienteRepository;

    private Usuario psicologo(String email) {
        return usuarioRepository.save(Fixtures.usuario(email));
    }

    private String bearerDe(Usuario usuario) {
        return "Bearer " + tokenService.gerarToken(usuario.getEmailUsuario());
    }

    @Test
    @DisplayName("GET /pacientes sem 'nome' devolve 200 com todos os pacientes do psicologo logado")
    void semNomeDevolveTodosOsPacientesDoLogado() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Usuario intruso = psicologo("intruso@teste.com");
        pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        pacienteRepository.save(Fixtures.paciente(dono, "Bruno"));
        pacienteRepository.save(Fixtures.paciente(intruso, "Carla"));

        mockMvc.perform(get("/pacientes").header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].nome")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("Ana", "Bruno")));
    }

    @Test
    @DisplayName("GET /pacientes?nome= (em branco) devolve todos os pacientes do psicologo logado")
    void nomeEmBrancoDevolveTodosOsPacientesDoLogado() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        pacienteRepository.save(Fixtures.paciente(dono, "Ana"));

        mockMvc.perform(get("/pacientes").param("nome", "").header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /pacientes?nome= ainda filtra pelo nome informado")
    void comNomeContinuaFiltrando() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        pacienteRepository.save(Fixtures.paciente(dono, "Bruno"));

        mockMvc.perform(get("/pacientes").param("nome", "an").header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Ana"));
    }
}
