package com.psychologyapi.psychologyapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.psychologyapi.psychologyapi.authsecurity.CookieRefreshToken;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * A renovacao so existe sobre HTTP: cookie, rotacao e o 401 da cadeia de
 * seguranca nao aparecem em nenhum teste de service.
 */
@HttpTest
class RefreshHttpTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private void psicologo(String email, String senha) {
        Usuario usuario = Fixtures.usuario(email);
        usuario.setSenhaUsuario(passwordEncoder.encode(senha));
        usuarioRepository.save(usuario);
    }

    private MvcResult login(String email, String senha) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailUsuario\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    @DisplayName("login devolve o refresh em cookie httpOnly")
    void loginDevolveCookieHttpOnly() throws Exception {
        psicologo("ana@teste.com", "senhaforte123");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailUsuario\":\"ana@teste.com\",\"senha\":\"senhaforte123\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(CookieRefreshToken.NOME))
                .andExpect(cookie().httpOnly(CookieRefreshToken.NOME, true))
                .andExpect(cookie().path(CookieRefreshToken.NOME, "/auth"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("refresh devolve access novo e rotaciona o cookie")
    void refreshRotaciona() throws Exception {
        psicologo("ana@teste.com", "senhaforte123");
        Cookie primeiro = login("ana@teste.com", "senhaforte123").getResponse().getCookie(CookieRefreshToken.NOME);

        MvcResult renovado = mockMvc.perform(post("/auth/refresh").cookie(primeiro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(cookie().exists(CookieRefreshToken.NOME))
                .andReturn();

        Cookie segundo = renovado.getResponse().getCookie(CookieRefreshToken.NOME);
        assertThat(segundo.getValue()).isNotEqualTo(primeiro.getValue());
    }

    @Test
    @DisplayName("refresh sem cookie responde 401 no envelope")
    void refreshSemCookieResponde401() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.mensagem").isNotEmpty())
                .andExpect(jsonPath("$.erros.length()").value(0));
    }

    @Test
    @DisplayName("o mesmo refresh usado duas vezes responde 401 na segunda")
    void reusoResponde401() throws Exception {
        psicologo("ana@teste.com", "senhaforte123");
        Cookie primeiro = login("ana@teste.com", "senhaforte123").getResponse().getCookie(CookieRefreshToken.NOME);

        mockMvc.perform(post("/auth/refresh").cookie(primeiro)).andExpect(status().isOk());

        mockMvc.perform(post("/auth/refresh").cookie(primeiro))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout responde 204 e o cookie deixa de servir")
    void logoutRevoga() throws Exception {
        psicologo("ana@teste.com", "senhaforte123");
        Cookie cookieDoLogin = login("ana@teste.com", "senhaforte123").getResponse().getCookie(CookieRefreshToken.NOME);

        mockMvc.perform(post("/auth/logout").cookie(cookieDoLogin))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh").cookie(cookieDoLogin))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout sem cookie tambem responde 204")
    void logoutSemCookieResponde204() throws Exception {
        mockMvc.perform(post("/auth/logout")).andExpect(status().isNoContent());
    }
}
