package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

/**
 * GET /consultas: sem parametros preserva o contrato atual (agenda de hoje).
 * Com 'de'/'ate' vira uma agenda de intervalo, essencial para telas de
 * semana/mes/calendario no frontend.
 */
@HttpTest
class ConsultaControllerTest {

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
    @DisplayName("GET /consultas sem parametros devolve so a agenda de hoje, como antes")
    void semParametrosDevolveAgendaDeHoje() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));

        consultaRepository.save(Fixtures.consulta(dono, paciente, LocalDateTime.now(), Status.AGENDADA));
        consultaRepository
                .save(Fixtures.consulta(dono, paciente, LocalDateTime.now().minusDays(2), Status.REALIZADA));

        mockMvc.perform(get("/consultas").header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /consultas?de=&ate= devolve o que esta dentro do intervalo e exclui o que esta fora")
    void comIntervaloFiltraDentroEForaDaJanela() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Usuario intruso = psicologo("intruso@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        Paciente pacienteIntruso = pacienteRepository.save(Fixtures.paciente(intruso, "Beto"));

        LocalDate de = LocalDate.of(2026, 9, 1);
        LocalDate ate = LocalDate.of(2026, 9, 7);

        consultaRepository.save(Fixtures.consulta(dono, paciente,
                LocalDateTime.of(2026, 9, 1, 8, 0), Status.AGENDADA)); // dentro, na borda inicial
        consultaRepository.save(Fixtures.consulta(dono, paciente,
                LocalDateTime.of(2026, 9, 7, 23, 59), Status.AGENDADA)); // dentro, na borda final
        consultaRepository.save(Fixtures.consulta(dono, paciente,
                LocalDateTime.of(2026, 8, 31, 23, 59), Status.AGENDADA)); // fora, na vespera
        consultaRepository.save(Fixtures.consulta(dono, paciente,
                LocalDateTime.of(2026, 9, 8, 12, 0), Status.AGENDADA)); // fora, no dia seguinte
        consultaRepository.save(Fixtures.consulta(intruso, pacienteIntruso,
                LocalDateTime.of(2026, 9, 3, 10, 0), Status.AGENDADA)); // dentro da janela, mas de outro psicologo

        mockMvc.perform(get("/consultas")
                .param("de", de.toString())
                .param("ate", ate.toString())
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /consultas?de= sem 'ate' devolve 400 com a lista de erros preenchida")
    void soDeInformadoDevolve400() throws Exception {
        Usuario dono = psicologo("dono@teste.com");

        mockMvc.perform(get("/consultas")
                .param("de", "2026-09-01")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros").isArray())
                .andExpect(jsonPath("$.erros.length()").value(1));
    }

    @Test
    @DisplayName("GET /consultas?ate= sem 'de' devolve 400 com a lista de erros preenchida")
    void soAteInformadoDevolve400() throws Exception {
        Usuario dono = psicologo("dono@teste.com");

        mockMvc.perform(get("/consultas")
                .param("ate", "2026-09-07")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros").isArray())
                .andExpect(jsonPath("$.erros.length()").value(1));
    }

    @Test
    @DisplayName("GET /consultas com 'ate' antes de 'de' devolve 400")
    void ateAntesDeDeDevolve400() throws Exception {
        Usuario dono = psicologo("dono@teste.com");

        mockMvc.perform(get("/consultas")
                .param("de", "2026-09-07")
                .param("ate", "2026-09-01")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("ate"));
    }
}
