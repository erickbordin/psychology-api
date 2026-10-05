package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.support.HttpTest;

@HttpTest
class TermosDivergentesHttpTest {

    private static final Path DIVERGENTE = diretorio();

    private static Path diretorio() {
        try {
            return Files.createTempDirectory("termos");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String uri(Path dir) {
        return "file:" + dir + "/";
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) throws IOException {
        Files.writeString(DIVERGENTE.resolve("termos.md"), "versao: a\n\nTexto de teste.\n");
        Files.writeString(DIVERGENTE.resolve("privacidade.md"), "versao: b\n\nTexto de teste.\n");
        registry.add("app.termos.diretorio", () -> uri(DIVERGENTE));
    }

    @Autowired
    private MockMvc mockMvc;

    private static final String CORPO_INVALIDO = "{\"email\":\"nao-e-email\"}";

    @Test
    @DisplayName("com versoes divergentes entre os arquivos, GET /termos/atual responde 404 no envelope")
    void termosAusentesNoGet() throws Exception {
        mockMvc.perform(get("/termos/atual"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Termos ainda não publicados."));
    }

    @Test
    @DisplayName("com versoes divergentes entre os arquivos, o cadastro responde 503 mesmo com corpo invalido")
    void termosAusentesNoCadastro() throws Exception {
        mockMvc.perform(post("/auth/registrar").contentType(MediaType.APPLICATION_JSON).content(CORPO_INVALIDO))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensagem").value("Cadastro ainda não está disponível."));
    }
}
