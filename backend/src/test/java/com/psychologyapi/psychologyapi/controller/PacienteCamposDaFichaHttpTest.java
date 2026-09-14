package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * Os tres campos de 5.4 na borda HTTP — valor de sessao, canal de aviso e
 * estado do consentimento — que o cabecalho da ficha (spec 4 (3)) precisa
 * mostrar. E a regra condicional: canal EMAIL exige e-mail.
 */
@HttpTest
class PacienteCamposDaFichaHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;

    private String token;

    @BeforeEach
    void preparar() {
        Usuario dono = usuarioRepository.save(Fixtures.usuario("dona.campos@example.com"));
        token = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());
    }

    private String cadastrar(String corpo) throws Exception {
        return mockMvc.perform(post("/pacientes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
               .andExpect(status().isCreated())
               .andReturn().getResponse().getContentAsString();
    }

    private String idDe(String json) {
        // o corpo e pequeno e a chave e unica: extrair por recorte evita
        // arrastar ObjectMapper para um teste que so precisa do id
        int i = json.indexOf("\"idPaciente\":\"") + 14;
        return json.substring(i, json.indexOf('"', i));
    }

    @Test
    @DisplayName("cadastro aceita valor de sessao e devolve nos tres campos da ficha")
    void cadastroAceitaEDevolveOsCamposDaFicha() throws Exception {
        mockMvc.perform(post("/pacientes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Helena Vaz","valorSessao":180.00,"canalAviso":"NENHUM"}
                                """))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.valorSessao").value(180.00))
               .andExpect(jsonPath("$.canalAviso").value("NENHUM"))
               .andExpect(jsonPath("$.consentimentoAvisoEm").doesNotExist());
    }

    @Test
    @DisplayName("valor de sessao zero ou negativo e recusado com o campo culpado")
    void valorDeSessaoPrecisaSerPositivo() throws Exception {
        mockMvc.perform(post("/pacientes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Helena Vaz","valorSessao":0}
                                """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[0].campo").value("valorSessao"))
               .andExpect(jsonPath("$.erros[0].erro").value("O valor da sessão deve ser maior que zero."));
    }

    @Test
    @DisplayName("valor com mais de duas casas e recusado")
    void valorDeSessaoTemDuasCasas() throws Exception {
        mockMvc.perform(post("/pacientes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Helena Vaz","valorSessao":180.005}
                                """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[0].campo").value("valorSessao"));
    }

    @Test
    @DisplayName("canal EMAIL sem e-mail e recusado — 5.4, validacao condicional")
    void canalEmailExigeEmail() throws Exception {
        mockMvc.perform(post("/pacientes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Helena Vaz","canalAviso":"EMAIL"}
                                """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[0].campo").value("email"))
               .andExpect(jsonPath("$.erros[0].erro").value(
                       "Informe o e-mail do paciente para avisar por e-mail."));
    }

    @Test
    @DisplayName("canal EMAIL com e-mail passa")
    void canalEmailComEmailPassa() throws Exception {
        mockMvc.perform(post("/pacientes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Helena Vaz","email":"helena@example.com","canalAviso":"EMAIL"}
                                """))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.canalAviso").value("EMAIL"));
    }

    @Test
    @DisplayName("canal NENHUM sem e-mail passa — e-mail continua opcional")
    void emailContinuaOpcionalSemCanalEmail() throws Exception {
        mockMvc.perform(post("/pacientes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Helena Vaz"}
                                """))
               .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("I6: editar o telefone NAO apaga o valor da sessao")
    void putParcialNaoApagaOQueNaoVeio() throws Exception {
        String id = idDe(cadastrar("""
                {"nome":"Helena Vaz","telefone":"51998112345","valorSessao":180.00}
                """));

        mockMvc.perform(put("/pacientes/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telefone":"51999998888"}
                                """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.telefone").value("51999998888"))
               .andExpect(jsonPath("$.valorSessao").value(180.00))
               .andExpect(jsonPath("$.nome").value("Helena Vaz"));

        mockMvc.perform(get("/pacientes/" + id).header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(jsonPath("$.valorSessao").value(180.00))
               .andExpect(jsonPath("$.nome").value("Helena Vaz"));
    }
}
