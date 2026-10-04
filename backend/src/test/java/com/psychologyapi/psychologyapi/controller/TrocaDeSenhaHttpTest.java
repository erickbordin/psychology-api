package com.psychologyapi.psychologyapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.psychologyapi.psychologyapi.authsecurity.CookieRefreshToken;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

import jakarta.servlet.http.Cookie;

@HttpTest
class TrocaDeSenhaHttpTest {

    private static final String EMAIL = "dona.senha@example.com";
    private static final String ATUAL = "Cavalo-Azul-Pulando-42";
    private static final String NOVA = "Montanha-Verde-Cantando-77";

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private Cookie cookieDoLogin;
    private String acesso;

    @BeforeEach
    void preparar() throws Exception {
        Usuario usuario = Fixtures.usuario(EMAIL);
        usuario.setSenhaUsuario(passwordEncoder.encode(ATUAL));
        usuarioRepository.save(usuario);
        MvcResult login = entrar(ATUAL);
        cookieDoLogin = login.getResponse().getCookie(CookieRefreshToken.NOME);
        acesso = "Bearer " + objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();
    }

    private MvcResult entrar(String senha) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailUsuario\":\"" + EMAIL + "\",\"senha\":\"" + senha + "\"}"))
                .andReturn();
    }

    private ResultActions trocar(String token, String atual, String nova) throws Exception {
        var requisicao = post("/ajustes/senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"" + atual + "\",\"novaSenha\":\"" + nova + "\"}");
        if (token != null) {
            requisicao.header(HttpHeaders.AUTHORIZATION, token);
        }
        return mockMvc.perform(requisicao);
    }

    private ResultActions renovar(Cookie cookie) throws Exception {
        return mockMvc.perform(post("/auth/refresh").cookie(cookie));
    }

    private ResultActions recusada(String atual, String nova, String campo, String mensagem) throws Exception {
        return trocar(acesso, atual, nova)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='" + campo + "')].erro").value(mensagem));
    }

    @Test
    @DisplayName("troca devolve 204 e um cookie de refresh novo")
    void trocaDevolve204ECookieNovo() throws Exception {
        MvcResult resultado = trocar(acesso, ATUAL, NOVA).andExpect(status().isNoContent()).andReturn();

        String setCookie = resultado.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains(CookieRefreshToken.NOME + "=", "HttpOnly", "Path=/auth");
        Cookie novo = resultado.getResponse().getCookie(CookieRefreshToken.NOME);
        assertThat(novo.getValue()).isNotBlank().isNotEqualTo(cookieDoLogin.getValue());
    }

    @Test
    @DisplayName("o cookie do login deixa de renovar depois da troca")
    void cookieAntigoMorre() throws Exception {
        trocar(acesso, ATUAL, NOVA).andExpect(status().isNoContent());

        renovar(cookieDoLogin).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a aba antiga recusada nao derruba o cookie novo da aba que trocou")
    void abaAntigaNaoDerrubaANova() throws Exception {
        Cookie novo = trocar(acesso, ATUAL, NOVA).andReturn().getResponse().getCookie(CookieRefreshToken.NOME);

        renovar(cookieDoLogin).andExpect(status().isUnauthorized());

        renovar(novo).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a troca encerra o refresh de outra sessao aberta")
    void outraSessaoMorre() throws Exception {
        Cookie outraAba = entrar(ATUAL).getResponse().getCookie(CookieRefreshToken.NOME);

        trocar(acesso, ATUAL, NOVA).andExpect(status().isNoContent());

        renovar(outraAba).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("depois da troca o login funciona com a senha nova e nao com a antiga")
    void entraComANova() throws Exception {
        trocar(acesso, ATUAL, NOVA).andExpect(status().isNoContent());

        assertThat(entrar(NOVA).getResponse().getStatus()).isEqualTo(200);
        assertThat(entrar(ATUAL).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("senha atual errada responde 400 e nao muda senha nem sessao")
    void senhaAtualErrada() throws Exception {
        recusada("Outra-Senha-Qualquer-99", NOVA, "senhaAtual", "A senha atual não confere.");

        assertThat(entrar(ATUAL).getResponse().getStatus()).isEqualTo(200);
        renovar(cookieDoLogin).andExpect(status().isOk());
    }

    @Test
    @DisplayName("nova senha igual a atual responde 400 no campo novaSenha")
    void novaIgualAAtual() throws Exception {
        recusada(ATUAL, ATUAL, "novaSenha", "A nova senha precisa ser diferente da atual.");
    }

    @Test
    @DisplayName("nova senha curta ou comum responde 400 com a mensagem do cadastro")
    void novaCurtaOuComum() throws Exception {
        recusada(ATUAL, "curta123", "novaSenha", "A senha precisa de pelo menos 10 caracteres.");
        recusada(ATUAL, "senha12345", "novaSenha", "Essa senha é fácil de adivinhar. Escolha outra.");
    }

    @Test
    @DisplayName("nova senha acima de 64 caracteres ou de 72 bytes responde 400 com a mensagem do limite")
    void novaLongaDemais() throws Exception {
        String mensagem = "A senha deve ter no máximo 64 caracteres.";
        recusada(ATUAL, "Ab3-".repeat(16) + "x", "novaSenha", mensagem);
        recusada(ATUAL, "ã".repeat(40), "novaSenha", mensagem);
    }

    @Test
    @DisplayName("nova senha forte de 64 caracteres e aceita")
    void novaNoLimite() throws Exception {
        trocar(acesso, ATUAL, "Ab3-".repeat(16)).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("senha atual longa e errada responde 400 e nao 500")
    void senhaAtualLongaErrada() throws Exception {
        recusada("x".repeat(100), NOVA, "senhaAtual", "A senha atual não confere.");
    }

    @Test
    @DisplayName("senha atual em branco responde 400 no campo senhaAtual")
    void senhaAtualEmBranco() throws Exception {
        recusada("", NOVA, "senhaAtual", "Informe a senha atual.");
    }

    @Test
    @DisplayName("sem token a troca responde 401")
    void semToken() throws Exception {
        trocar(null, ATUAL, NOVA).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a trilha registra a troca e ignora a troca recusada")
    void trilhaRegistra() throws Exception {
        trocar(acesso, ATUAL, NOVA).andExpect(status().isNoContent());

        mockMvc.perform(get("/auditoria").param("entidade", "Senha").header(HttpHeaders.AUTHORIZATION, acesso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].acao").value("ATUALIZACAO"));

        trocar(acesso, "Outra-Senha-Qualquer-99", "Terceira-Senha-Valida-55").andExpect(status().isBadRequest());

        mockMvc.perform(get("/auditoria").param("entidade", "Senha").header(HttpHeaders.AUTHORIZATION, acesso))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("a troca apaga tambem uma cadeia de refresh rotacionada sem violar a chave estrangeira")
    void cadeiaRotacionadaNaoQuebraOApagar() throws Exception {
        Cookie segundo = renovar(cookieDoLogin).andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieRefreshToken.NOME);
        renovar(segundo).andExpect(status().isOk());

        trocar(acesso, ATUAL, NOVA).andExpect(status().isNoContent());
    }
}
