package com.psychologyapi.psychologyapi.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * C2 do laudo do conjunto (2026-09-05): o contrato declara um formato de data
 * (RFC 3339 pelo `format: date-time` do OpenAPI, offset obrigatorio) e as
 * bordas antigas aceitavam tres coisas diferentes — com e sem segundos, com
 * offset (descartado em silencio) e sem offset. Com TZ=America/Sao_Paulo
 * (spec 5.9), aceitar "Z" e descartar o offset grava a hora ERRADA sem erro
 * nenhum.
 *
 * Fechado: as duas bordas (GET /consultas/conflito, via @DateTimeFormat de
 * pattern estrito; POST /consultas, via @JsonFormat) so aceitam
 * yyyy-MM-dd'T'HH:mm:ss, sem offset. Um valor com offset ou sem hora e
 * RECUSADO, nunca convertido silenciosamente.
 *
 * Nota sobre o envelope de erro: a leitura (query param) e a escrita (corpo)
 * tomavam caminhos DIFERENTES pelo GlobalExceptionsHandler — a escrita cai no
 * ErroRespostaDTO via HttpMessageNotReadableException, mas a leitura escapava
 * dele (corpo generico do Spring, sem "mensagem" nem "erros") porque nada
 * tratava MethodArgumentTypeMismatchException. Fechado: as duas bordas agora
 * respondem no mesmo envelope, com "erros[*].campo" apontando "dtConsulta" do
 * lado da leitura. Os testes abaixo cobram as duas coisas: o mesmo status nas
 * duas bordas, e o corpo do envelope tambem na leitura.
 *
 * Re-revisao (2026-09-09, laudo-c1-c2-rodada-2.md): a leitura (GET
 * /consultas/conflito) aceitava "sem segundos" e "fracao de segundo" — 200 —
 * enquanto a escrita (POST /consultas) recusava as duas — 400. Causa: o
 * @DateTimeFormat(pattern=...) do @RequestParam, sem fallbackPatterns(),
 * cai silenciosamente para LocalDateTime.parse(texto) (ISO_LOCAL_DATE_TIME,
 * que trata segundos/fracao como opcionais) quando o padrao estrito nao
 * casa — ver FormatoDeDataConfig para o mecanismo completo. Fechado com um
 * Converter<String, LocalDateTime> registrado como bean, que vence a
 * conversao do parametro e nao tem esse fallback. Os testes abaixo cobrem as
 * duas formas que vazavam, mais lixo apos os segundos.
 */
@HttpTest
class ConsultaFormatoDeDataHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;

    private String tokenDoDono;
    private Paciente helena;

    @BeforeEach
    void preparar() {
        Usuario dono = usuarioRepository.save(Fixtures.usuario("dona.formato@example.com"));
        tokenDoDono = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());
        helena = pacienteRepository.save(Fixtures.paciente(dono, "Helena Vaz"));
    }

    @Test
    @DisplayName("o formato que o contrato declara (sem offset, com segundos) e aceito nas duas bordas, "
            + "e volta exatamente assim no corpo da resposta")
    void formatoDeclaradoEAceitoNasDuasBordasEVoltaIgualNaResposta() throws Exception {
        mockMvc.perform(get("/consultas/conflito")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .param("dtConsulta", "2026-12-02T14:00:00"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.livre").value(true));

        mockMvc.perform(post("/consultas")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"pacienteId":"%s","dtConsulta":"2026-12-02T14:00:00"}
                                 """.formatted(helena.getId())))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.dtConsulta").value("2026-12-02T14:00:00"));
    }

    /**
     * O caso mais importante do conserto. Medido ANTES da correcao: "Z" era
     * aceito nas duas bordas e o offset descartado em silencio — com
     * TZ=America/Sao_Paulo, 14:00:00Z e 11:00 no consultorio, e a API gravava
     * 14:00, sem erro, 201. Agora as duas recusam.
     */
    @Test
    @DisplayName("offset 'Z' (UTC) e recusado nas duas bordas, com o mesmo status (400) — antes era "
            + "aceito e a hora gravada silenciosamente errada")
    void offsetZERecusadoNasDuasBordasComOMesmoStatus() throws Exception {
        mockMvc.perform(get("/consultas/conflito")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .param("dtConsulta", "2026-12-02T14:00:00Z"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.mensagem").exists())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("dtConsulta")));

        mockMvc.perform(post("/consultas")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"pacienteId":"%s","dtConsulta":"2026-12-02T14:00:00Z"}
                                 """.formatted(helena.getId())))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.mensagem").exists())
               .andExpect(jsonPath("$.erros").isArray());
    }

    /**
     * Medido ANTES da correcao: a leitura respondia 200 (livre=true/false) e
     * a escrita respondia 400 para a MESMA string — "o front pergunta, e
     * dito que sim, envia e apanha", a falha que /conflito existe para
     * eliminar. Agora as duas recusam com o mesmo status.
     */
    @Test
    @DisplayName("offset '-03:00' e recusado nas duas bordas, com o mesmo status — antes a leitura "
            + "dizia 200/livre e a escrita respondia 400 para a MESMA string")
    void offsetNegativoERecusadoNasDuasBordasComOMesmoStatus() throws Exception {
        mockMvc.perform(get("/consultas/conflito")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .param("dtConsulta", "2026-12-02T14:00:00-03:00"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.mensagem").exists())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("dtConsulta")));

        mockMvc.perform(post("/consultas")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"pacienteId":"%s","dtConsulta":"2026-12-02T14:00:00-03:00"}
                                 """.formatted(helena.getId())))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.mensagem").exists())
               .andExpect(jsonPath("$.erros").isArray());
    }

    @Test
    @DisplayName("data sem hora e recusada nas duas bordas, com o mesmo status")
    void dataSemHoraERecusadaNasDuasBordasComOMesmoStatus() throws Exception {
        mockMvc.perform(get("/consultas/conflito")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .param("dtConsulta", "2026-12-02"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.mensagem").exists())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("dtConsulta")));

        mockMvc.perform(post("/consultas")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"pacienteId":"%s","dtConsulta":"2026-12-02"}
                                 """.formatted(helena.getId())))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.mensagem").exists())
               .andExpect(jsonPath("$.erros").isArray());
    }

    /**
     * Achado da re-revisao (laudo-c1-c2-rodada-2.md): ANTES deste conserto,
     * "2026-12-02T14:00" (sem segundos) respondia 200 em GET /consultas/conflito
     * e 400 em POST /consultas — a MESMA divergencia leitura x escrita que C2
     * ja tinha catalogado, so que para um formato que ninguem pediu para
     * aceitar. Agora as duas recusam.
     */
    @Test
    @DisplayName("sem segundos (HH:mm) e recusado nas duas bordas, com o mesmo status — antes a "
            + "leitura aceitava (200) e a escrita recusava (400) para a MESMA string")
    void semSegundosERecusadoNasDuasBordasComOMesmoStatus() throws Exception {
        mockMvc.perform(get("/consultas/conflito")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .param("dtConsulta", "2026-12-02T14:00"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.mensagem").exists())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("dtConsulta")));

        mockMvc.perform(post("/consultas")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"pacienteId":"%s","dtConsulta":"2026-12-02T14:00"}
                                 """.formatted(helena.getId())))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.mensagem").exists())
               .andExpect(jsonPath("$.erros").isArray());
    }

    /**
     * Mesmo achado da re-revisao, para fracao de segundo. ANTES: 200 na
     * leitura, 400 na escrita, para a mesma string.
     */
    @Test
    @DisplayName("fracao de segundo (HH:mm:ss.SSS) e recusada nas duas bordas, com o mesmo status — "
            + "antes a leitura aceitava (200) e a escrita recusava (400) para a MESMA string")
    void fracaoDeSegundoERecusadaNasDuasBordasComOMesmoStatus() throws Exception {
        mockMvc.perform(get("/consultas/conflito")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .param("dtConsulta", "2026-12-02T14:00:00.123"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.mensagem").exists())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("dtConsulta")));

        mockMvc.perform(post("/consultas")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"pacienteId":"%s","dtConsulta":"2026-12-02T14:00:00.123"}
                                 """.formatted(helena.getId())))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.mensagem").exists())
               .andExpect(jsonPath("$.erros").isArray());
    }

    /**
     * Nao fazia parte da divergencia medida (ja recusava 400/400 antes), mas
     * a instrucao pede a cobertura explicitamente: lixo apos os segundos nao
     * pode ser aceito em nenhuma borda.
     */
    @Test
    @DisplayName("texto sobrando apos os segundos e recusado nas duas bordas, com o mesmo status")
    void textoSobrandoAposOsSegundosERecusadoNasDuasBordasComOMesmoStatus() throws Exception {
        mockMvc.perform(get("/consultas/conflito")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .param("dtConsulta", "2026-12-02T14:00:00extra"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.mensagem").exists())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("dtConsulta")));

        mockMvc.perform(post("/consultas")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"pacienteId":"%s","dtConsulta":"2026-12-02T14:00:00extra"}
                                 """.formatted(helena.getId())))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.mensagem").exists())
               .andExpect(jsonPath("$.erros").isArray());
    }
}
