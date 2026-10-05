package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.authsecurity.CookieRefreshToken;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

@HttpTest
@TestPropertySource(properties = "app.refresh.cookie-path=/api/auth")
class CookiePathHttpTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("o cookie de refresh sai com o caminho configurado atras do proxy")
    void cookieUsaOCaminhoConfigurado() throws Exception {
        Usuario usuario = Fixtures.usuario("ana@teste.com");
        usuario.setSenhaUsuario(passwordEncoder.encode("senhaforte123"));
        usuarioRepository.save(usuario);

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailUsuario\":\"ana@teste.com\",\"senha\":\"senhaforte123\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().path(CookieRefreshToken.NOME, "/api/auth"));
    }
}
