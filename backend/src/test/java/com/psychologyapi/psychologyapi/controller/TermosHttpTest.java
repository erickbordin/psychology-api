package com.psychologyapi.psychologyapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.model.entity.LogAuditoria;
import com.psychologyapi.psychologyapi.repository.LogAuditoriaRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.support.HttpTest;

@HttpTest
class TermosHttpTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private LogAuditoriaRepository logAuditoriaRepository;

    private static String corpo(String aceite, String versao) {
        return """
                {"codigoConvite":"teste-1","nome":"Psicologa Termos","email":"termos@teste.com","senha":"senhaforte123"%s%s}
                """.formatted(aceite, versao);
    }

    private org.springframework.test.web.servlet.ResultActions registrar(String json) throws Exception {
        return mockMvc.perform(post("/auth/registrar").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @DisplayName("GET /termos/atual e publico e devolve a versao e os dois textos sem a linha de versao")
    void termosAtualPublico() throws Exception {
        mockMvc.perform(get("/termos/atual"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value("teste-1"))
                .andExpect(jsonPath("$.termos").value("Texto de teste. Não é o termo do produto."))
                .andExpect(jsonPath("$.privacidade").value("Texto de teste. Não é a política do produto."));
    }

    @Test
    @DisplayName("cadastro sem aceiteTermos responde 400 no campo aceiteTermos")
    void semAceite() throws Exception {
        registrar(corpo("", ",\"versaoTermos\":\"teste-1\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='aceiteTermos')].erro")
                        .value(hasItem("Aceite os termos para criar a conta.")));
    }

    @Test
    @DisplayName("cadastro com aceiteTermos false responde 400 no campo aceiteTermos")
    void aceiteFalso() throws Exception {
        registrar(corpo(",\"aceiteTermos\":false", ",\"versaoTermos\":\"teste-1\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='aceiteTermos')].erro")
                        .value(hasItem("Aceite os termos para criar a conta.")));
    }

    @Test
    @DisplayName("cadastro com versaoTermos em branco responde 400 no campo versaoTermos")
    void versaoEmBranco() throws Exception {
        registrar(corpo(",\"aceiteTermos\":true", ",\"versaoTermos\":\" \""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='versaoTermos')].erro")
                        .value(hasItem("Informe a versão dos termos.")));
    }

    @Test
    @DisplayName("cadastro com versao diferente da atual responde 409 pedindo a leitura da versao atual")
    void versaoDiferente() throws Exception {
        registrar(corpo(",\"aceiteTermos\":true", ",\"versaoTermos\":\"teste-0\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Os termos mudaram. Leia a versão atual."));

        assertThat(usuarioRepository.existsByEmailUsuario("termos@teste.com")).isFalse();
    }

    @Test
    @DisplayName("cadastro com aceite e versao certa responde 201 e grava versao, data e trilha do aceite")
    void aceiteGravado() throws Exception {
        registrar(corpo(",\"aceiteTermos\":true", ",\"versaoTermos\":\"teste-1\""))
                .andExpect(status().isCreated());

        Usuario usuario = usuarioRepository.findByEmailUsuario("termos@teste.com").orElseThrow();
        assertThat(usuario.getTermosVersao()).isEqualTo("teste-1");
        assertThat(usuario.getTermosAceitosEm()).isNotNull();

        List<LogAuditoria> trilha = logAuditoriaRepository.findAll().stream()
                .filter(l -> usuario.getId().equals(l.getEntidadeId()))
                .toList();
        assertThat(trilha).hasSize(1);
        assertThat(trilha.get(0).getAcao()).isEqualTo(AcaoAuditoria.CRIACAO);
        assertThat(trilha.get(0).getEntidade()).isEqualTo("AceiteDosTermos");
    }
}
