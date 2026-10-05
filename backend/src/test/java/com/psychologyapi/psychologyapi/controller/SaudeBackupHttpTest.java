package com.psychologyapi.psychologyapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.support.HttpTest;

@HttpTest
class SaudeBackupHttpTest {

    private static final Path ARQUIVO = criarArquivo();

    private static Path criarArquivo() {
        try {
            return Files.createTempDirectory("saude-backup").resolve("estado");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("app.backup.arquivo-de-estado", () -> ARQUIVO.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void limpar() throws IOException {
        Files.deleteIfExists(ARQUIVO);
    }

    private static String horas(long atras) {
        return LocalDateTime.now().minusHours(atras).withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private static void escrever(String dump, String teste, String testeOk, String falha) throws IOException {
        Files.writeString(ARQUIVO, "ultimo_dump_ok=" + dump + "\n"
                + "ultimo_teste=" + teste + "\n"
                + "ultimo_teste_ok=" + testeOk + "\n"
                + "falha=" + falha + "\n");
    }

    @Test
    @DisplayName("arquivo recente e sem falha responde 200 com backup ok")
    void backupEmDia() throws Exception {
        escrever(horas(2), horas(1), "true", "");

        mockMvc.perform(get("/saude"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.backup").value("ok"));
    }

    @Test
    @DisplayName("dump de 37 horas atras responde 503 atrasado")
    void dumpAntigoResponde503() throws Exception {
        escrever(horas(37), horas(1), "true", "");

        mockMvc.perform(get("/saude"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.backup").value("atrasado"));
    }

    @Test
    @DisplayName("teste de restauracao que falhou responde 503")
    void testeDeRestauracaoFalhouResponde503() throws Exception {
        escrever(horas(2), horas(1), "false", "");

        mockMvc.perform(get("/saude"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.backup").value("atrasado"));
    }

    @Test
    @DisplayName("falha registrada responde 503 sem devolver o texto da falha")
    void falhaRegistradaResponde503SemVazar() throws Exception {
        escrever(horas(2), horas(1), "true", "envio");

        String corpo = mockMvc.perform(get("/saude"))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("envio");
    }

    @Test
    @DisplayName("arquivo ausente responde 503")
    void arquivoAusenteResponde503() throws Exception {
        mockMvc.perform(get("/saude"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.backup").value("atrasado"));
    }

    @Test
    @DisplayName("arquivo ilegivel responde 503")
    void arquivoIlegivelResponde503() throws Exception {
        Files.write(ARQUIVO, new byte[] {(byte) 0xC3, (byte) 0x28, (byte) 0xFF});

        mockMvc.perform(get("/saude"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.backup").value("atrasado"));
    }

    @Test
    @DisplayName("data invalida no arquivo responde 503")
    void dataInvalidaResponde503() throws Exception {
        escrever("ontem", horas(1), "true", "");

        mockMvc.perform(get("/saude"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.backup").value("atrasado"));
    }

    @Test
    @DisplayName("a rota e publica e o corpo nunca traz a data do dump")
    void rotaPublicaSemVazarData() throws Exception {
        String dump = horas(2);
        escrever(dump, horas(1), "true", "");

        String corpo = mockMvc.perform(get("/saude"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain(dump.substring(0, 10)).doesNotContain(ARQUIVO.toString());
    }

    @Test
    @DisplayName("dump de 35 horas atras ainda responde 200 com backup ok")
    void dumpDentroDoLimiteResponde200() throws Exception {
        escrever(horas(35), horas(1), "true", "");

        mockMvc.perform(get("/saude"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backup").value("ok"));
    }

    @Test
    @DisplayName("arquivo so com dump recente e falha vazia responde 200 com backup ok")
    void arquivoSemChavesDeTesteResponde200() throws Exception {
        Files.writeString(ARQUIVO, "ultimo_dump_ok=" + horas(2) + "\nfalha=\n");

        mockMvc.perform(get("/saude"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backup").value("ok"));
    }
}
