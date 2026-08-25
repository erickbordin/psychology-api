package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

@HttpTest
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private void psicologoComSenha(String email, String senha) {
        Usuario usuario = Fixtures.usuario(email);
        usuario.setSenhaUsuario(passwordEncoder.encode(senha));
        usuarioRepository.save(usuario);
    }

    @Test
    @DisplayName("POST /auth/registrar responde 201 com o usuario criado, sem devolver senha")
    void registrarResponde201() throws Exception {
        mockMvc.perform(post("/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Psicologa Ana\",\"email\":\"ana@teste.com\",\"senha\":\"senhaforte123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailUsuario").value("ana@teste.com"))
                .andExpect(jsonPath("$.senhaUsuario").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    @Test
    @DisplayName("POST /auth/registrar com email duplicado responde 409")
    void registrarDuplicadoResponde409() throws Exception {
        psicologoComSenha("ana@teste.com", "senhaforte123");

        mockMvc.perform(post("/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Psicologa Ana\",\"email\":\"ana@teste.com\",\"senha\":\"senhaforte123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /auth/registrar sem campo obrigatorio responde 400")
    void registrarSemCampoResponde400() throws Exception {
        mockMvc.perform(post("/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@teste.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[*].campo").value(org.hamcrest.Matchers.hasItems("nome", "senha")));
    }

    @Test
    @DisplayName("POST /auth/login devolve um token utilizavel")
    void loginDevolveToken() throws Exception {
        psicologoComSenha("ana@teste.com", "senhaforte123");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailUsuario\":\"ana@teste.com\",\"senha\":\"senhaforte123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("POST /auth/login com senha errada responde 401")
    void loginComSenhaErradaResponde401() throws Exception {
        psicologoComSenha("ana@teste.com", "senhaforte123");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailUsuario\":\"ana@teste.com\",\"senha\":\"senhaerrada\"}"))
                .andExpect(status().isUnauthorized());
    }
}
