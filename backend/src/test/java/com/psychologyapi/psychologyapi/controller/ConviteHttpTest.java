package com.psychologyapi.psychologyapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.psychologyapi.psychologyapi.model.ConviteUsado;
import com.psychologyapi.psychologyapi.repository.ConviteUsadoRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.support.HttpTest;

@HttpTest
@TestPropertySource(properties = "app.cadastro.convites=conv-a,conv-b,conv-c")
class ConviteHttpTest {

    private static final String MENSAGEM = "Código de convite inválido.";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ConviteUsadoRepository conviteUsadoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    private ResultActions registrar(String codigo, String email) throws Exception {
        String convite = codigo == null ? "" : "\"codigoConvite\":\"" + codigo + "\",";
        return mockMvc.perform(post("/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + convite + "\"aceiteTermos\":true,\"versaoTermos\":\"teste-1\",\"nome\":\"Psicologa Convite\",\"email\":\""
                        + email + "\",\"senha\":\"senhaforte123\"}"));
    }

    private static String sha256(String codigo) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(codigo.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("convite valido responde 201 e grava o hash do codigo, nunca o codigo")
    void conviteValido() throws Exception {
        registrar("conv-a", "um@teste.com").andExpect(status().isCreated());

        assertThat(conviteUsadoRepository.findAll()).extracting(ConviteUsado::getHash)
                .containsExactly(sha256("conv-a"))
                .doesNotContain("conv-a");
    }

    @Test
    @DisplayName("convite com espacos nas pontas vale como o codigo aparado")
    void conviteComEspacos() throws Exception {
        registrar("  conv-b  ", "dois@teste.com").andExpect(status().isCreated());

        assertThat(conviteUsadoRepository.existsByHash(sha256("conv-b"))).isTrue();
    }

    @Test
    @DisplayName("convite ja usado responde 403 com a mensagem unica")
    void conviteReusado() throws Exception {
        registrar("conv-a", "um@teste.com").andExpect(status().isCreated());

        registrar("conv-a", "outro@teste.com")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.mensagem").value(MENSAGEM))
                .andExpect(jsonPath("$.erros.length()").value(0));
        assertThat(usuarioRepository.existsByEmailUsuario("outro@teste.com")).isFalse();
    }

    @Test
    @DisplayName("convite desconhecido responde 403 com a mesma mensagem do reutilizado")
    void conviteDesconhecido() throws Exception {
        registrar("nao-existe", "tres@teste.com")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value(MENSAGEM));
        assertThat(usuarioRepository.existsByEmailUsuario("tres@teste.com")).isFalse();
    }

    @Test
    @DisplayName("convite em branco responde 400 pedindo o codigo no campo codigoConvite")
    void conviteEmBranco() throws Exception {
        registrar(" ", "quatro@teste.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='codigoConvite')].erro").value("Informe o código de convite."));
    }

    @Test
    @DisplayName("convite ausente responde 400 pedindo o codigo no campo codigoConvite")
    void conviteAusente() throws Exception {
        registrar(null, "cinco@teste.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='codigoConvite')].erro").value("Informe o código de convite."));
    }

    @Test
    @DisplayName("convite invalido nao aparece na resposta")
    void conviteNaoVazaNaResposta() throws Exception {
        String corpo = registrar("segredo-xyz", "seis@teste.com")
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("segredo-xyz");
    }

    @Test
    @DisplayName("e-mail repetido com convite novo responde 409 e o convite segue valido")
    void emailRepetidoNaoQueimaOConvite() throws Exception {
        registrar("conv-a", "repetido@teste.com").andExpect(status().isCreated());

        registrar("conv-b", "repetido@teste.com").andExpect(status().isConflict());
        assertThat(conviteUsadoRepository.existsByHash(sha256("conv-b"))).isFalse();

        registrar("conv-b", "novo@teste.com").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("versao dos termos desatualizada responde 409 e nao queima o convite")
    void versaoDesatualizadaNaoQueimaOConvite() throws Exception {
        mockMvc.perform(post("/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoConvite\":\"conv-c\",\"aceiteTermos\":true,\"versaoTermos\":\"teste-0\",\"nome\":\"Psicologa Convite\",\"email\":\"sete@teste.com\",\"senha\":\"senhaforte123\"}"))
                .andExpect(status().isConflict());

        assertThat(conviteUsadoRepository.existsByHash(sha256("conv-c"))).isFalse();
    }
}
