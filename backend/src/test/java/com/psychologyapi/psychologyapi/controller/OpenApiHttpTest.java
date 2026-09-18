package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * O contrato OpenAPI de onde o front gera os tipos (spec 9). Precisa ser
 * alcancavel sem autenticacao — mas so ele, nenhuma rota de dado junto.
 */
@HttpTest
class OpenApiHttpTest {

    private static final String CAMPO_DATA_DO_PAINEL =
            "$.components.schemas.SessaoDoDiaDTO.properties.dtConsulta";

    private static final String VALOR_NO_CADASTRO =
            "$.components.schemas.PacienteCadastroDTO.properties.valorSessao";
    private static final String VALOR_NA_ATUALIZACAO =
            "$.components.schemas.PacienteAtualizacaoDTO.properties.valorSessao";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("o contrato OpenAPI e alcancavel sem autenticacao")
    void contratoEPublico() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.openapi").exists());
    }

    @Test
    @DisplayName("o contrato descreve os endpoints da Fatia 1")
    void contratoDescreveAFatia1() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
               .andExpect(jsonPath("$.paths['/painel/hoje']").exists())
               .andExpect(jsonPath("$.paths['/consultas/conflito']").exists())
               .andExpect(jsonPath("$.paths['/auth/login']").exists());
    }

    @Test
    @DisplayName("expor o contrato nao abre nenhuma rota de dado")
    void contratoNaoAbreRotaDeDado() throws Exception {
        // sem token: /pacientes continua fechado
        mockMvc.perform(get("/pacientes"))
               .andExpect(status().isUnauthorized());
    }

    /**
     * Re-revisao C2 (2026-09-09, laudo-c1-c2-rodada-2.md): o conserto
     * anterior corrigiu so os quatro campos de ENTRADA de LocalDateTime e
     * deixou todo campo de SAIDA declarado como {"type":"string","format":
     * "date-time"} — RFC 3339 com offset, na convencao OpenAPI/JSON Schema —
     * enquanto a API nunca emite offset (spec 5.9). Fechado globalmente em
     * OpenApiConfig via SpringDocUtils.replaceWithSchema(LocalDateTime.class,
     * ...). SessaoDoDiaDTO.dtConsulta e o campo de data de /painel/hoje, a
     * tela que e o proprio motivo da Fatia 1 existir — se algum LocalDateTime
     * de saida ficar de fora do registro global, este e o mais provavel de
     * doer primeiro.
     */
    @Test
    @DisplayName("um campo de data de SAIDA (/painel/hoje) declara string sem offset, "
            + "sem mais 'format: date-time'")
    void campoDeDataDeSaidaDoPainelDeclaraFormatoSemOffset() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
               .andExpect(jsonPath(CAMPO_DATA_DO_PAINEL + ".type").value("string"))
               .andExpect(jsonPath(CAMPO_DATA_DO_PAINEL + ".format").doesNotExist())
               .andExpect(jsonPath(CAMPO_DATA_DO_PAINEL + ".example").value("2026-12-02T14:00:00"));
    }

    /**
     * F1 da revisao da Fatia 2 — o contrato mentia sobre {@code valorSessao}.
     *
     * Ele publicava {@code {"type":"number","minimum":0}}, ou seja: zero e
     * aceito e nao ha limite de casas decimais. A API recusa os dois — o DTO
     * traz {@code @DecimalMin(inclusive = false)} e
     * {@code @Digits(integer = 8, fraction = 2)}. Um cliente gerado a partir do
     * contrato deixaria passar {@code 0} e {@code 180.005} e so descobriria o
     * erro no 400 do servidor.
     *
     * Esta e a mesma classe de defeito que a revisao da Fatia 1 encontrou nos
     * campos de data: o contrato gerado e a UNICA rede automatica contra
     * divergencia entre front e API, e ela tinha buraco. Por isso a assercao
     * mora aqui — para o contrato nao poder voltar a divergir em silencio.
     *
     * A regra e afirmada como {@code minimum: 0.01} e nao como
     * {@code exclusiveMinimum}: com {@code multipleOf: 0.01}, um centavo e o
     * menor valor representavel, entao a forma inclusiva diz exatamente a
     * mesma coisa que a exclusiva — e diz numa chave que este documento
     * consegue publicar. O que NAO pode voltar e {@code minimum: 0}: seria o
     * contrato aceitando o zero que o {@code @DecimalMin(inclusive = false)}
     * recusa, que e o defeito que este teste existe para prender.
     */
    @Test
    @DisplayName("o contrato declara valorSessao como a API realmente valida: "
            + "maior que zero e com duas casas")
    void contratoDescreveAsRegrasDeValorSessao() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
               .andExpect(jsonPath(VALOR_NO_CADASTRO + ".type").value("number"))
               .andExpect(jsonPath(VALOR_NO_CADASTRO + ".minimum").value(0.01))
               .andExpect(jsonPath(VALOR_NO_CADASTRO + ".multipleOf").value(0.01))
               .andExpect(jsonPath(VALOR_NO_CADASTRO + ".maximum").value(99999999.99))

               .andExpect(jsonPath(VALOR_NA_ATUALIZACAO + ".minimum").value(0.01))
               .andExpect(jsonPath(VALOR_NA_ATUALIZACAO + ".multipleOf").value(0.01))
               .andExpect(jsonPath(VALOR_NA_ATUALIZACAO + ".maximum").value(99999999.99));
    }

    /**
     * F2 da revisao da Fatia 2 — o mesmo {@code createdAt} de anotacao viajava
     * em dois formatos. {@code AnotacaoResponseDTO.createdAt} era
     * {@code LocalDate} (o mapper gerado chamava {@code .toLocalDate()} e
     * truncava a hora), enquanto {@code AnotacaoDaSessaoDTO.createdAt} e
     * {@code LocalDateTime}: gravar a anotacao devolvia {@code "2026-09-17"} e
     * abrir a ficha devolvia {@code "2026-09-17T14:03:11"} para o MESMO
     * registro. Num prontuario, a hora do lancamento faz parte do lancamento
     * (spec 7.3).
     *
     * Unificado em {@code LocalDateTime}, que o registro global do
     * {@code OpenApiConfig} ja serializa sem offset (spec 5.9) — por isso a
     * ausencia de {@code format} tambem e afirmada: {@code format: date} de
     * volta significaria o truncamento de volta.
     */
    @Test
    @DisplayName("createdAt de anotacao e o mesmo tipo nos dois DTOs: data E hora, sem offset")
    void createdAtDeAnotacaoEUnicoNosDoisDTOs() throws Exception {
        String naResposta = "$.components.schemas.AnotacaoResponseDTO.properties.createdAt";
        String naFicha = "$.components.schemas.AnotacaoDaSessaoDTO.properties.createdAt";

        mockMvc.perform(get("/v3/api-docs"))
               .andExpect(jsonPath(naResposta + ".type").value("string"))
               .andExpect(jsonPath(naResposta + ".format").doesNotExist())
               .andExpect(jsonPath(naResposta + ".example").value("2026-12-02T14:00:00"))
               .andExpect(jsonPath(naFicha + ".type").value("string"))
               .andExpect(jsonPath(naFicha + ".format").doesNotExist())
               .andExpect(jsonPath(naFicha + ".example").value("2026-12-02T14:00:00"));
    }

    /**
     * D2 da revisao da Fatia 2 — a convencao "branco limpa, ausente nao mexe"
     * so existia no codigo e no comentario do DTO. O contrato e de onde o front
     * gera os tipos: uma regra que ele nao conta e uma regra que o cliente nao
     * tem como respeitar.
     */
    @Test
    @DisplayName("o contrato conta que e-mail e telefone em branco limpam o campo no PUT")
    void contratoDocumentaALimpezaPorValorEmBranco() throws Exception {
        String email = "$.components.schemas.PacienteAtualizacaoDTO.properties.email.description";
        String telefone = "$.components.schemas.PacienteAtualizacaoDTO.properties.telefone.description";

        mockMvc.perform(get("/v3/api-docs"))
               .andExpect(jsonPath(email).value(org.hamcrest.Matchers.containsString("em branco")))
               .andExpect(jsonPath(telefone).value(org.hamcrest.Matchers.containsString("em branco")));
    }
}
