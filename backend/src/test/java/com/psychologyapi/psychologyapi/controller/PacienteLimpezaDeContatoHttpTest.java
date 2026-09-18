package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.hamcrest.Matchers.hasItem;
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
 * A convencao de limpeza de campo de contato no PUT (decisao do dono, revisao
 * da Fatia 2):
 *
 * <ul>
 *   <li>campo enviado EM BRANCO ({@code ""}) limpa o que estava salvo;</li>
 *   <li>campo AUSENTE do corpo continua intocado — a garantia de PUT parcial
 *       da Fatia 2 ({@code NullValuePropertyMappingStrategy.IGNORE}).</li>
 * </ul>
 *
 * Vale so para {@code email} e {@code telefone}. Os demais campos seguem com a
 * regra antiga: sem mecanismo de limpeza.
 *
 * Toda afirmacao de limpeza e confirmada por um GET depois do PUT: a resposta
 * do proprio PUT e montada a partir da entidade em memoria e nao provaria que
 * o valor foi PERSISTIDO como nulo.
 */
@HttpTest
class PacienteLimpezaDeContatoHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;

    private String token;

    @BeforeEach
    void preparar() {
        Usuario dono = usuarioRepository.save(Fixtures.usuario("dona.limpeza@example.com"));
        token = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());
    }

    private String cadastrar(String corpo) throws Exception {
        String json = mockMvc.perform(post("/pacientes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int i = json.indexOf("\"idPaciente\":\"") + 14;
        return json.substring(i, json.indexOf('"', i));
    }

    private void atualizar(String id, String corpo, int statusEsperado) throws Exception {
        mockMvc.perform(put("/pacientes/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
               .andExpect(status().is(statusEsperado));
    }

    @Test
    @DisplayName("PUT com e-mail em branco limpa o e-mail salvo")
    void emailEmBrancoLimpa() throws Exception {
        String id = cadastrar("""
                {"nome":"Helena Vaz","email":"helena@example.com","telefone":"51998112345"}
                """);

        atualizar(id, "{\"email\":\"\"}", 200);

        mockMvc.perform(get("/pacientes/" + id).header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(jsonPath("$.email").doesNotExist())
               // o telefone, que nem foi citado no corpo, continua inteiro
               .andExpect(jsonPath("$.telefone").value("51998112345"));
    }

    @Test
    @DisplayName("PUT sem a chave email nao mexe no e-mail salvo")
    void emailAusenteNaoMexe() throws Exception {
        String id = cadastrar("""
                {"nome":"Helena Vaz","email":"helena@example.com"}
                """);

        atualizar(id, "{\"nome\":\"Helena Vaz Ribeiro\"}", 200);

        mockMvc.perform(get("/pacientes/" + id).header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(jsonPath("$.email").value("helena@example.com"))
               .andExpect(jsonPath("$.nome").value("Helena Vaz Ribeiro"));
    }

    @Test
    @DisplayName("PUT com telefone em branco limpa o telefone salvo")
    void telefoneEmBrancoLimpa() throws Exception {
        String id = cadastrar("""
                {"nome":"Helena Vaz","email":"helena@example.com","telefone":"51998112345"}
                """);

        atualizar(id, "{\"telefone\":\"\"}", 200);

        mockMvc.perform(get("/pacientes/" + id).header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(jsonPath("$.telefone").doesNotExist())
               .andExpect(jsonPath("$.email").value("helena@example.com"));
    }

    @Test
    @DisplayName("PUT sem a chave telefone nao mexe no telefone salvo")
    void telefoneAusenteNaoMexe() throws Exception {
        String id = cadastrar("""
                {"nome":"Helena Vaz","telefone":"51998112345"}
                """);

        atualizar(id, "{\"nome\":\"Helena Vaz Ribeiro\"}", 200);

        mockMvc.perform(get("/pacientes/" + id).header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(jsonPath("$.telefone").value("51998112345"));
    }

    // ---------------------------------------------------------------------
    // A regra condicional de spec 5.4 no PUT: ela le o estado EFETIVO
    // (payload sobre o que ja esta salvo), nao so o corpo da requisicao.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("PUT so com canalAviso EMAIL passa quando o paciente JA tem e-mail salvo")
    void canalEmailSozinhoPassaComEmailJaSalvo() throws Exception {
        String id = cadastrar("""
                {"nome":"Helena Vaz","email":"helena@example.com"}
                """);

        atualizar(id, "{\"canalAviso\":\"EMAIL\"}", 200);

        mockMvc.perform(get("/pacientes/" + id).header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(jsonPath("$.canalAviso").value("EMAIL"))
               .andExpect(jsonPath("$.email").value("helena@example.com"));
    }

    @Test
    @DisplayName("PUT so com canalAviso EMAIL e recusado quando o paciente nao tem e-mail")
    void canalEmailSozinhoERecusadoSemEmail() throws Exception {
        String id = cadastrar("""
                {"nome":"Helena Vaz"}
                """);

        mockMvc.perform(put("/pacientes/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canalAviso\":\"EMAIL\"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("email")))
               .andExpect(jsonPath("$.erros[*].erro").value(
                       hasItem("Informe o e-mail do paciente para avisar por e-mail.")));
    }

    @Test
    @DisplayName("limpar o e-mail de um paciente cujo canal e EMAIL e recusado, com a mesma mensagem")
    void limparEmailComCanalEmailERecusado() throws Exception {
        String id = cadastrar("""
                {"nome":"Helena Vaz","email":"helena@example.com","canalAviso":"EMAIL"}
                """);

        mockMvc.perform(put("/pacientes/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("email")))
               .andExpect(jsonPath("$.erros[*].erro").value(
                       hasItem("Informe o e-mail do paciente para avisar por e-mail.")));

        // e o e-mail continua salvo: a recusa nao pode deixar meio-estado
        mockMvc.perform(get("/pacientes/" + id).header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(jsonPath("$.email").value("helena@example.com"));
    }

    @Test
    @DisplayName("trocar canal e e-mail no mesmo PUT continua valido")
    void trocarCanalEEmailJuntosPassa() throws Exception {
        String id = cadastrar("""
                {"nome":"Helena Vaz"}
                """);

        atualizar(id, "{\"email\":\"helena.nova@example.com\",\"canalAviso\":\"EMAIL\"}", 200);

        mockMvc.perform(get("/pacientes/" + id).header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(jsonPath("$.email").value("helena.nova@example.com"))
               .andExpect(jsonPath("$.canalAviso").value("EMAIL"));
    }
}
