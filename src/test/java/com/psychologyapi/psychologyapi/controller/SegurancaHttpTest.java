package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * O que so aparece sobre HTTP: o filtro JWT, a cadeia de seguranca e o
 * GlobalExceptionsHandler. Nenhuma dessas tres coisas e exercitada pelos
 * testes de service, que chamam o metodo direto.
 */
@HttpTest
class SegurancaHttpTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PacienteRepository pacienteRepository;

    private Usuario psicologo(String email) {
        return usuarioRepository.save(Fixtures.usuario(email));
    }

    private String bearerDe(Usuario usuario) {
        return "Bearer " + tokenService.gerarToken(usuario.getEmailUsuario());
    }

    @Test
    @DisplayName("rota protegida sem token responde 401 no mesmo envelope do 400")
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get("/auditoria"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.mensagem").isNotEmpty())
                .andExpect(jsonPath("$.erros").isArray())
                .andExpect(jsonPath("$.erros.length()").value(0));
    }

    @Test
    @DisplayName("rota protegida com token malformado responde 401, nao 500")
    void tokenMalformadoResponde401() throws Exception {
        mockMvc.perform(get("/auditoria").header(HttpHeaders.AUTHORIZATION, "Bearer isso.nao.e.um.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("token assinado com outro segredo responde 401")
    void tokenComAssinaturaInvalidaResponde401() throws Exception {
        String tokenDeOutroSegredo = "eyJhbGciOiJIUzI1NiJ9"
                + ".eyJzdWIiOiJkb25vQHRlc3RlLmNvbSIsImlhdCI6MTcwMDAwMDAwMCwiZXhwIjo0MDAwMDAwMDAwfQ"
                + ".assinaturaquenaoconfereconosegredodotestexxxxxxxxxxxxxxxxxxx";

        mockMvc.perform(get("/auditoria").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeOutroSegredo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /pacientes/{id} de outro psicologo responde 404, nunca 403")
    void pacienteDeOutroPsicologoResponde404() throws Exception {
        Usuario dono = psicologo("dono@teste.com");
        Usuario intruso = psicologo("intruso@teste.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));

        mockMvc.perform(get("/pacientes/" + paciente.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerDe(intruso)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensagem").isNotEmpty())
                .andExpect(jsonPath("$.erros").isArray())
                .andExpect(jsonPath("$.erros.length()").value(0));
    }

    @Test
    @DisplayName("GET /pacientes/{id} inexistente responde 404")
    void pacienteInexistenteResponde404() throws Exception {
        Usuario dono = psicologo("dono@teste.com");

        mockMvc.perform(get("/pacientes/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /pacientes sem campo obrigatorio responde 400 com campo e mensagem")
    void validacaoResponde400ComListaDeCampos() throws Exception {
        Usuario dono = psicologo("dono@teste.com");

        mockMvc.perform(post("/pacientes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"telefone\":\"51999999999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erros").isArray())
                .andExpect(jsonPath("$.erros[*].campo").value(org.hamcrest.Matchers.hasItems("nome",
                        "dataNascimento")));
    }
}
