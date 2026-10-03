package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
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
class PagamentoHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private ConsultaRepository consultaRepository;

    private String token;
    private Usuario dono;
    private Paciente bruna;
    private Consulta deOutro;

    @BeforeEach
    void preparar() {
        dono = usuarioRepository.save(Fixtures.usuario("dona.pagamento@example.com"));
        token = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());
        Paciente paciente = Fixtures.paciente(dono, "Bruna Teixeira");
        paciente.setValorSessao(new BigDecimal("180.00"));
        bruna = pacienteRepository.save(paciente);

        Usuario outro = usuarioRepository.save(Fixtures.usuario("outra.pagamento@example.com"));
        Paciente pacienteDeOutro = pacienteRepository.save(Fixtures.paciente(outro, "Carla Moreira"));
        Consulta alheia = Fixtures.consulta(outro, pacienteDeOutro,
                LocalDateTime.parse("2026-12-02T10:00:00"), Status.REALIZADA);
        alheia.setValor(new BigDecimal("150.00"));
        deOutro = consultaRepository.save(alheia);
    }

    private Consulta sessao(Status status, String valor, LocalDateTime pagoEm) {
        Consulta consulta = Fixtures.consulta(dono, bruna, LocalDateTime.parse("2026-12-02T14:00:00"), status);
        consulta.setValor(valor == null ? null : new BigDecimal(valor));
        consulta.setPagoEm(pagoEm);
        return consultaRepository.save(consulta);
    }

    private Consulta realizadaAberta() {
        return sessao(Status.REALIZADA, "180.00", null);
    }

    private Consulta paga() {
        return sessao(Status.REALIZADA, "180.00", LocalDateTime.parse("2026-12-02T15:00:00"));
    }

    @Test
    @DisplayName("PATCH baixa numa realizada com valor devolve pagoEm e o valor")
    void baixaFeliz() throws Exception {
        Consulta consulta = realizadaAberta();

        mockMvc.perform(patch("/consultas/" + consulta.getId() + "/baixa")
                .header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.pagoEm").exists())
               .andExpect(jsonPath("$.valor").value(180.0));
    }

    @Test
    @DisplayName("PATCH baixa duas vezes devolve o mesmo pagoEm")
    void baixaIdempotente() throws Exception {
        Consulta consulta = realizadaAberta();

        MvcResult primeira = mockMvc.perform(patch("/consultas/" + consulta.getId() + "/baixa")
                .header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isOk())
               .andReturn();
        MvcResult segunda = mockMvc.perform(patch("/consultas/" + consulta.getId() + "/baixa")
                .header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isOk())
               .andReturn();

        String pagoEmPrimeira = JsonPath.read(primeira.getResponse().getContentAsString(), "$.pagoEm");
        String pagoEmSegunda = JsonPath.read(segunda.getResponse().getContentAsString(), "$.pagoEm");
        assertThat(pagoEmSegunda).isEqualTo(pagoEmPrimeira);
    }

    @Test
    @DisplayName("PATCH baixa numa sessao agendada responde 409 com a frase certa")
    void baixaEmAgendada() throws Exception {
        Consulta consulta = sessao(Status.AGENDADA, null, null);

        mockMvc.perform(patch("/consultas/" + consulta.getId() + "/baixa")
                .header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.mensagem")
                       .value("Só sessão realizada recebe baixa. Marque a sessão como realizada antes."))
               .andExpect(jsonPath("$.erros").isEmpty());
    }

    @Test
    @DisplayName("PATCH baixa numa realizada sem valor responde 409 pedindo o valor")
    void baixaSemValor() throws Exception {
        Consulta consulta = sessao(Status.REALIZADA, null, null);

        mockMvc.perform(patch("/consultas/" + consulta.getId() + "/baixa")
                .header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.mensagem").value("Defina o valor antes de dar baixa."));
    }

    @Test
    @DisplayName("DELETE baixa numa sessao paga devolve 200 sem pagoEm")
    void desfazerBaixa() throws Exception {
        Consulta consulta = paga();

        mockMvc.perform(delete("/consultas/" + consulta.getId() + "/baixa")
                .header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.pagoEm").doesNotExist());
    }

    @Test
    @DisplayName("POST baixas com duas abertas baixa as duas e o GET mostra pagoEm em cada uma")
    void loteFeliz() throws Exception {
        Consulta primeira = realizadaAberta();
        Consulta segunda = realizadaAberta();

        mockMvc.perform(post("/consultas/baixas")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"consultaIds\":[\"" + primeira.getId() + "\",\"" + segunda.getId() + "\"]}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.consultasBaixadas").value(2));

        for (Consulta consulta : new Consulta[] {primeira, segunda}) {
            mockMvc.perform(get("/consultas/" + consulta.getId()).header(HttpHeaders.AUTHORIZATION, token))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$.pagoEm").exists());
        }
    }

    @Test
    @DisplayName("POST baixas com lista vazia responde 400 no campo consultaIds")
    void loteVazio() throws Exception {
        mockMvc.perform(post("/consultas/baixas")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"consultaIds\":[]}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[0].campo").value("consultaIds"));
    }

    @Test
    @DisplayName("POST baixas com um id de outro psicologo responde 404 e a aberta continua sem baixa")
    void loteComIdDeOutro() throws Exception {
        Consulta aberta = realizadaAberta();

        mockMvc.perform(post("/consultas/baixas")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"consultaIds\":[\"" + aberta.getId() + "\",\"" + deOutro.getId() + "\"]}"))
               .andExpect(status().isNotFound());

        mockMvc.perform(get("/consultas/" + aberta.getId()).header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.pagoEm").doesNotExist());
    }

    @Test
    @DisplayName("PATCH valor numa realizada sem baixa troca o valor")
    void alterarValor() throws Exception {
        Consulta consulta = realizadaAberta();

        mockMvc.perform(patch("/consultas/" + consulta.getId() + "/valor")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\":90.5}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.valor").value(90.5));
    }

    @Test
    @DisplayName("PATCH valor zero responde 400 no campo valor")
    void valorZero() throws Exception {
        Consulta consulta = realizadaAberta();

        mockMvc.perform(patch("/consultas/" + consulta.getId() + "/valor")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\":0}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[0].campo").value("valor"))
               .andExpect(jsonPath("$.erros[0].erro").value("O valor da sessão deve ser maior que zero."));
    }

    @Test
    @DisplayName("PATCH valor com tres casas decimais responde 400 no campo valor")
    void valorComTresCasas() throws Exception {
        Consulta consulta = realizadaAberta();

        mockMvc.perform(patch("/consultas/" + consulta.getId() + "/valor")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\":10.123}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[0].campo").value("valor"))
               .andExpect(jsonPath("$.erros[0].erro")
                       .value("O valor da sessão deve ter no máximo duas casas decimais."));
    }

    @Test
    @DisplayName("PATCH valor sem o campo responde 400 no campo valor")
    void valorAusente() throws Exception {
        Consulta consulta = realizadaAberta();

        mockMvc.perform(patch("/consultas/" + consulta.getId() + "/valor")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[0].campo").value("valor"))
               .andExpect(jsonPath("$.erros[0].erro").value("Informe o valor da sessão."));
    }

    @Test
    @DisplayName("PATCH valor numa sessao paga responde 409 mandando desfazer a baixa")
    void valorEmPaga() throws Exception {
        Consulta consulta = paga();

        mockMvc.perform(patch("/consultas/" + consulta.getId() + "/valor")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\":90.5}"))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.mensagem")
                       .value("Esta sessão já tem baixa. Desfaça a baixa antes de alterar o valor."));
    }

    @Test
    @DisplayName("PATCH baixa na consulta de outro psicologo responde 404")
    void baixaDeOutro() throws Exception {
        mockMvc.perform(patch("/consultas/" + deOutro.getId() + "/baixa")
                .header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH baixa sem token responde 401")
    void baixaSemToken() throws Exception {
        Consulta consulta = realizadaAberta();

        mockMvc.perform(patch("/consultas/" + consulta.getId() + "/baixa"))
               .andExpect(status().isUnauthorized());
    }
}
