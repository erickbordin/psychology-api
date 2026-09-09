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
    @DisplayName("POST /auth/registrar com email duplicado responde 409 no envelope de erro")
    void registrarDuplicadoResponde409() throws Exception {
        psicologoComSenha("ana@teste.com", "senhaforte123");

        mockMvc.perform(post("/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Psicologa Ana\",\"email\":\"ana@teste.com\",\"senha\":\"senhaforte123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensagem").isNotEmpty())
                .andExpect(jsonPath("$.erros").isArray())
                .andExpect(jsonPath("$.erros.length()").value(0));
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
    @DisplayName("senha curta e recusada com 400 (spec 6.1: unica falha de seguranca da analise)")
    void senhaCurtaERecusada() throws Exception {
        // cadastro valido em tudo, menos a senha
        mockMvc.perform(post("/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"nome":"Marina Aguiar","email":"marina@example.com","senha":"a"}
                         """))
               .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/registrar com senha comum responde 400 apontando o campo")
    void registrarComSenhaComumResponde400() throws Exception {
        mockMvc.perform(post("/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"nome":"Marina Aguiar","email":"marina.senhacomum@example.com","senha":"senha123456"}
                         """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[*].campo").value(org.hamcrest.Matchers.hasItem("senha")))
               .andExpect(jsonPath("$.erros[*].erro")
                       .value(org.hamcrest.Matchers.hasItem("Essa senha é fácil de adivinhar. Escolha outra.")));
    }

    @Test
    @DisplayName("POST /auth/login com senha errada responde 401 no envelope de erro")
    void loginComSenhaErradaResponde401() throws Exception {
        psicologoComSenha("ana@teste.com", "senhaforte123");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailUsuario\":\"ana@teste.com\",\"senha\":\"senhaerrada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.mensagem").isNotEmpty())
                .andExpect(jsonPath("$.erros").isArray())
                .andExpect(jsonPath("$.erros.length()").value(0));
    }
}
