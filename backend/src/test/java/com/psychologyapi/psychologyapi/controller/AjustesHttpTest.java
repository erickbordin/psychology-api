package com.psychologyapi.psychologyapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.model.entity.LogAuditoria;
import com.psychologyapi.psychologyapi.repository.LogAuditoriaRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

@HttpTest
class AjustesHttpTest {

    private static final String CORPO_COMPLETO = """
            {"nome":"Dra. Helena Prado","valorPadraoSessao":200.50,"antecedencia":"H2",
             "blocosAviso":["REMARCAR","CHEGADA"],"tituloNoEmail":true,
             "tituloProfissional":"Psicóloga clínica","resumoDiario":false,
             "expedienteInicio":8,"expedienteFim":18}""";

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private LogAuditoriaRepository logAuditoriaRepository;

    private Usuario dono;
    private String token;

    @BeforeEach
    void preparar() {
        dono = usuarioRepository.save(Fixtures.usuario("dona.ajustes@example.com"));
        token = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());
    }

    private ResultActions enviar(String corpo) throws Exception {
        return mockMvc.perform(put("/ajustes")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private ResultActions recusado(String corpo, String campo, String mensagem) throws Exception {
        return enviar(corpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='" + campo + "')].erro").value(mensagem));
    }

    private static String corpo(String nome, String valor, String antecedencia, String titulo,
            String tituloNoEmail, String inicio, String fim) {
        return "{\"nome\":" + nome + ",\"valorPadraoSessao\":" + valor + ",\"antecedencia\":" + antecedencia
                + ",\"blocosAviso\":[\"REMARCAR\"],\"tituloNoEmail\":" + tituloNoEmail
                + ",\"tituloProfissional\":" + titulo + ",\"resumoDiario\":true,"
                + "\"expedienteInicio\":" + inicio + ",\"expedienteFim\":" + fim + "}";
    }

    private static String valido() {
        return corpo("\"Helena\"", "null", "\"H24\"", "null", "false", "null", "null");
    }

    @Test
    @DisplayName("GET devolve os padroes de um usuario novo e o e-mail dele")
    void padroesDoUsuarioNovo() throws Exception {
        mockMvc.perform(get("/ajustes").header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.email").value("dona.ajustes@example.com"))
               .andExpect(jsonPath("$.antecedencia").value("H24"))
               .andExpect(jsonPath("$.blocosAviso", hasSize(1)))
               .andExpect(jsonPath("$.blocosAviso[0]").value("REMARCAR"))
               .andExpect(jsonPath("$.tituloNoEmail").value(false))
               .andExpect(jsonPath("$.resumoDiario").value(true))
               .andExpect(jsonPath("$.expedienteInicio").doesNotExist())
               .andExpect(jsonPath("$.expedienteFim").doesNotExist());
    }

    @Test
    @DisplayName("PUT completo persiste e o GET seguinte devolve igual")
    void putPersiste() throws Exception {
        enviar(CORPO_COMPLETO).andExpect(status().isOk());

        mockMvc.perform(get("/ajustes").header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.nome").value("Dra. Helena Prado"))
               .andExpect(jsonPath("$.email").value("dona.ajustes@example.com"))
               .andExpect(jsonPath("$.valorPadraoSessao").value(200.5))
               .andExpect(jsonPath("$.antecedencia").value("H2"))
               .andExpect(jsonPath("$.blocosAviso", hasSize(2)))
               .andExpect(jsonPath("$.tituloNoEmail").value(true))
               .andExpect(jsonPath("$.tituloProfissional").value("Psicóloga clínica"))
               .andExpect(jsonPath("$.resumoDiario").value(false))
               .andExpect(jsonPath("$.expedienteInicio").value(8))
               .andExpect(jsonPath("$.expedienteFim").value(18));
    }

    @Test
    @DisplayName("PUT sem valor, sem expediente e sem blocos limpa tudo")
    void putLimpa() throws Exception {
        enviar(CORPO_COMPLETO).andExpect(status().isOk());
        enviar("""
                {"nome":"Helena","antecedencia":"H24","tituloNoEmail":false,"resumoDiario":true}""")
                .andExpect(status().isOk());

        mockMvc.perform(get("/ajustes").header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(jsonPath("$.valorPadraoSessao").doesNotExist())
               .andExpect(jsonPath("$.expedienteInicio").doesNotExist())
               .andExpect(jsonPath("$.blocosAviso", hasSize(0)));
    }

    @Test
    @DisplayName("titulo em branco com a caixa desligada grava nulo")
    void tituloEmBrancoGravaNulo() throws Exception {
        enviar(corpo("\"Helena\"", "null", "\"H24\"", "\"   \"", "false", "null", "null"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tituloProfissional").doesNotExist());
    }

    @Test
    @DisplayName("titulo no e-mail ligado exige titulo")
    void tituloObrigatorioComCaixaLigada() throws Exception {
        recusado(corpo("\"Helena\"", "null", "\"H24\"", "\"  \"", "true", "null", "null"),
                "tituloProfissional", "Informe o título profissional para incluí-lo no e-mail.");
    }

    @Test
    @DisplayName("titulo com 81 caracteres e recusado")
    void tituloLongo() throws Exception {
        recusado(corpo("\"Helena\"", "null", "\"H24\"", "\"" + "a".repeat(81) + "\"", "false", "null", "null"),
                "tituloProfissional", "O título deve ter no máximo 80 caracteres.");
    }

    @Test
    @DisplayName("so o inicio do expediente e recusado no campo do fim")
    void soInicio() throws Exception {
        recusado(corpo("\"Helena\"", "null", "\"H24\"", "null", "false", "8", "null"),
                "expedienteFim", "Informe o início e o fim do expediente, ou nenhum dos dois.");
    }

    @Test
    @DisplayName("so o fim do expediente e recusado no campo do inicio, que falta")
    void soFim() throws Exception {
        recusado(corpo("\"Helena\"", "null", "\"H24\"", "null", "false", "null", "18"),
                "expedienteInicio", "Informe o início e o fim do expediente, ou nenhum dos dois.");
    }

    @Test
    @DisplayName("fim antes do inicio e recusado no campo do fim")
    void fimAntesDoInicio() throws Exception {
        recusado(corpo("\"Helena\"", "null", "\"H24\"", "null", "false", "18", "8"),
                "expedienteFim", "O fim do expediente deve ser depois do início.");
    }

    @Test
    @DisplayName("fim igual ao inicio e recusado")
    void fimIgualAoInicio() throws Exception {
        recusado(corpo("\"Helena\"", "null", "\"H24\"", "null", "false", "8", "8"),
                "expedienteFim", "O fim do expediente deve ser depois do início.");
    }

    @Test
    @DisplayName("inicio 24 e recusado")
    void inicioForaDaFaixa() throws Exception {
        recusado(corpo("\"Helena\"", "null", "\"H24\"", "null", "false", "24", "24"),
                "expedienteInicio", "O início do expediente deve estar entre 0 e 23.");
    }

    @Test
    @DisplayName("fim 0 e recusado")
    void fimForaDaFaixa() throws Exception {
        recusado(corpo("\"Helena\"", "null", "\"H24\"", "null", "false", "0", "0"),
                "expedienteFim", "O fim do expediente deve estar entre 1 e 24.");
    }

    @Test
    @DisplayName("nome com 1 caractere e recusado")
    void nomeCurto() throws Exception {
        recusado(corpo("\"H\"", "null", "\"H24\"", "null", "false", "null", "null"),
                "nome", "O nome deve ter entre 2 e 100 caracteres.");
    }

    @Test
    @DisplayName("nome de um caractere mais espaco e recusado e nada muda")
    void nomeCurtoComEspaco() throws Exception {
        recusado(corpo("\"H \"", "null", "\"H24\"", "null", "false", "null", "null"),
                "nome", "O nome deve ter entre 2 e 100 caracteres.");

        mockMvc.perform(get("/ajustes").header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(jsonPath("$.nome").value(dono.getNomeUsuario()));
    }

    @Test
    @DisplayName("blocos com null ignoram o null e guardam so o resto")
    void blocosComNull() throws Exception {
        enviar("""
                {"nome":"Helena","antecedencia":"H24","blocosAviso":["REMARCAR",null],
                 "tituloNoEmail":false,"resumoDiario":true}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocosAviso", hasSize(1)))
                .andExpect(jsonPath("$.blocosAviso[0]").value("REMARCAR"));
    }

    @Test
    @DisplayName("nome em branco e recusado")
    void nomeEmBranco() throws Exception {
        recusado(corpo("\"  \"", "null", "\"H24\"", "null", "false", "null", "null"),
                "nome", "Informe seu nome.");
    }

    @Test
    @DisplayName("sem antecedencia e recusado")
    void semAntecedencia() throws Exception {
        recusado(corpo("\"Helena\"", "null", "null", "null", "false", "null", "null"),
                "antecedencia", "Escolha a antecedência do aviso.");
    }

    @Test
    @DisplayName("valor padrao zero e recusado")
    void valorZero() throws Exception {
        recusado(corpo("\"Helena\"", "0", "\"H24\"", "null", "false", "null", "null"),
                "valorPadraoSessao", "O valor da sessão deve ser maior que zero.");
    }

    @Test
    @DisplayName("PUT recusado nao muda nada")
    void recusadoNaoMuda() throws Exception {
        enviar(corpo("\"Outro Nome\"", "null", "\"H24\"", "null", "false", "18", "8"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/ajustes").header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(jsonPath("$.nome").value(dono.getNomeUsuario()));
    }

    @Test
    @DisplayName("PUT audita ATUALIZACAO de Ajustes com o id do usuario")
    void audita() throws Exception {
        enviar(valido()).andExpect(status().isOk());

        var logs = logAuditoriaRepository
                .findByUsuarioAndEntidadeAndEntidadeIdOrderByCreatedAtDesc(
                        dono, "Ajustes", dono.getId(), PageRequest.of(0, 10))
                .getContent();

        assertThat(logs).hasSize(1);
        assertThat(logs).extracting(LogAuditoria::getAcao).containsExactly(AcaoAuditoria.ATUALIZACAO);
    }

    @Test
    @DisplayName("um psicologo nao enxerga nem altera os ajustes do outro")
    void isolamento() throws Exception {
        Usuario outro = usuarioRepository.save(Fixtures.usuario("outra.ajustes@example.com"));
        String tokenDoOutro = "Bearer " + tokenService.gerarToken(outro.getEmailUsuario());

        enviar(CORPO_COMPLETO).andExpect(status().isOk());

        mockMvc.perform(get("/ajustes").header(HttpHeaders.AUTHORIZATION, tokenDoOutro))
               .andExpect(jsonPath("$.email").value("outra.ajustes@example.com"))
               .andExpect(jsonPath("$.antecedencia").value("H24"))
               .andExpect(jsonPath("$.resumoDiario").value(true));
    }

    @Test
    @DisplayName("sem token, GET e PUT respondem 401")
    void semToken() throws Exception {
        mockMvc.perform(get("/ajustes")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/ajustes").contentType(MediaType.APPLICATION_JSON).content(valido()))
               .andExpect(status().isUnauthorized());
    }
}
