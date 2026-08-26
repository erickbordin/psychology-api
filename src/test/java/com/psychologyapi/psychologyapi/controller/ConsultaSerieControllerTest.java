package com.psychologyapi.psychologyapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

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
class ConsultaSerieControllerTest {

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

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private Usuario psicologo(String email) {
        return usuarioRepository.save(Fixtures.usuario(email));
    }

    private String bearerDe(Usuario usuario) {
        return "Bearer " + tokenService.gerarToken(usuario.getEmailUsuario());
    }

    private LocalDateTime primeiraOcorrencia() {
        return LocalDateTime.now().plusDays(7).withHour(15).withMinute(0).withSecond(0).withNano(0);
    }

    private String corpoPorQuantidade(UUID pacienteId, LocalDateTime primeira, int quantidade) {
        return """
                {"pacienteId":"%s","dtPrimeiraConsulta":"%s","quantidadeSessoes":%d}
                """.formatted(pacienteId, primeira.format(ISO), quantidade);
    }

    @Test
    @DisplayName("POST /consultas/recorrentes devolve 201 com o serieId e as consultas")
    void cadastraSerie() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        LocalDateTime primeira = primeiraOcorrencia();

        mockMvc.perform(post("/consultas/recorrentes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoPorQuantidade(paciente.getId(), primeira, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serieId").isNotEmpty())
                .andExpect(jsonPath("$.quantidade").value(3))
                .andExpect(jsonPath("$.consultas.length()").value(3))
                .andExpect(jsonPath("$.consultas[0].status").value("AGENDADA"));
    }

    @Test
    @DisplayName("POST /consultas/recorrentes devolve 409 com as datas em conflito e nao cria nada")
    void conflitoAbortaASerie() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        LocalDateTime primeira = primeiraOcorrencia();

        consultaRepository.save(Fixtures.consulta(dono, paciente, primeira.plusWeeks(2), Status.AGENDADA));
        long antes = consultaRepository.count();

        mockMvc.perform(post("/consultas/recorrentes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoPorQuantidade(paciente.getId(), primeira, 4)))
                .andExpect(status().isConflict());

        assertThat(consultaRepository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("POST /consultas/recorrentes com os dois horizontes devolve 400 com a lista de erros preenchida")
    void doisHorizontesJuntos() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        LocalDateTime primeira = primeiraOcorrencia();

        String corpo = """
                {"pacienteId":"%s","dtPrimeiraConsulta":"%s","quantidadeSessoes":4,"dtLimite":"%s"}
                """.formatted(paciente.getId(), primeira.format(ISO), primeira.plusWeeks(9).toLocalDate());

        mockMvc.perform(post("/consultas/recorrentes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros").isArray())
                .andExpect(jsonPath("$.erros.length()").value(1))
                .andExpect(jsonPath("$.erros[0].campo").value("quantidadeSessoes"));
    }

    @Test
    @DisplayName("POST /consultas/recorrentes sem horizonte nenhum devolve 400")
    void nenhumHorizonte() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        LocalDateTime primeira = primeiraOcorrencia();

        String corpo = """
                {"pacienteId":"%s","dtPrimeiraConsulta":"%s"}
                """.formatted(paciente.getId(), primeira.format(ISO));

        mockMvc.perform(post("/consultas/recorrentes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("quantidadeSessoes"));
    }

    @Test
    @DisplayName("DELETE /consultas/series/{id} cancela as futuras e devolve a contagem")
    void cancelaSerie() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        UUID serieId = UUID.randomUUID();

        consultaRepository.save(Fixtures.consultaDeSerie(dono, paciente, LocalDateTime.now().minusWeeks(1), serieId));
        consultaRepository.save(Fixtures.consultaDeSerie(dono, paciente, LocalDateTime.now().plusWeeks(1), serieId));
        consultaRepository.save(Fixtures.consultaDeSerie(dono, paciente, LocalDateTime.now().plusWeeks(2), serieId));

        mockMvc.perform(delete("/consultas/series/" + serieId)
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serieId").value(serieId.toString()))
                .andExpect(jsonPath("$.ocorrenciasRemovidas").value(2));
    }

    @Test
    @DisplayName("DELETE /consultas/series/{id} de serie de outro psicologo devolve 404")
    void cancelaSerieDeOutroPsicologo() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Usuario intruso = psicologo("intruso@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        UUID serieId = UUID.randomUUID();

        consultaRepository.save(Fixtures.consultaDeSerie(dono, paciente, LocalDateTime.now().plusWeeks(1), serieId));

        mockMvc.perform(delete("/consultas/series/" + serieId)
                .header(HttpHeaders.AUTHORIZATION, bearerDe(intruso)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("as consultas do 201 carregam o mesmo serieId do envelope")
    void consultasCarregamOSerieIdDoEnvelope() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        LocalDateTime primeira = primeiraOcorrencia();

        String corpo = mockMvc.perform(post("/consultas/recorrentes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoPorQuantidade(paciente.getId(), primeira, 3)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String serieId = JsonPath.read(corpo, "$.serieId");
        List<String> serieIdDosItens = JsonPath.read(corpo, "$.consultas[*].serieId");

        assertThat(serieId).isNotBlank();
        assertThat(serieIdDosItens).hasSize(3).containsOnly(serieId);
    }

    @Test
    @DisplayName("GET /consultas/{id} devolve o serieId da ocorrencia; consulta avulsa vem com serieId nulo")
    void serieIdApareceNaLeituraIndividual() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        LocalDateTime primeira = primeiraOcorrencia();

        String corpoDaSerie = mockMvc.perform(post("/consultas/recorrentes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoPorQuantidade(paciente.getId(), primeira, 2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String serieId = JsonPath.read(corpoDaSerie, "$.serieId");
        String idDaOcorrencia = JsonPath.read(corpoDaSerie, "$.consultas[0].id");

        mockMvc.perform(get("/consultas/" + idDaOcorrencia)
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serieId").value(serieId));

        String corpoAvulso = "{\"pacienteId\":\"" + paciente.getId()
                + "\",\"dtConsulta\":\"" + primeira.plusDays(3).format(ISO) + "\"}";

        mockMvc.perform(post("/consultas")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoAvulso))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serieId").value(org.hamcrest.Matchers.nullValue()));
    }
}
