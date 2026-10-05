package com.psychologyapi.psychologyapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

class TermosServiceTest {

    @TempDir
    Path dir;

    private TermosService servico() {
        return new TermosService(new DefaultResourceLoader(), "file:" + dir + "/");
    }

    private void escrever(String arquivo, String conteudo) throws IOException {
        Files.writeString(dir.resolve(arquivo), conteudo);
    }

    @Test
    @DisplayName("com os dois arquivos na mesma versao devolve versao e textos aparados, sem a linha de versao")
    void disponivel() throws IOException {
        escrever("termos.md", "versao: v1\n\n  Texto de teste dos termos.  \n\n");
        escrever("privacidade.md", "versao: v1\nTexto de teste da privacidade.\n");

        var termos = servico().atual();

        assertThat(termos).isPresent();
        assertThat(termos.get().versao()).isEqualTo("v1");
        assertThat(termos.get().termos()).isEqualTo("Texto de teste dos termos.");
        assertThat(termos.get().privacidade()).isEqualTo("Texto de teste da privacidade.");
    }

    @Test
    @DisplayName("arquivo ausente deixa os termos indisponiveis")
    void arquivoAusente() throws IOException {
        escrever("termos.md", "versao: v1\n\nTexto de teste.\n");

        assertThat(servico().atual()).isEmpty();
    }

    @Test
    @DisplayName("primeira linha sem versao deixa os termos indisponiveis")
    void semVersao() throws IOException {
        escrever("termos.md", "Texto de teste.\n");
        escrever("privacidade.md", "versao: v1\n\nTexto de teste.\n");

        assertThat(servico().atual()).isEmpty();
    }

    @Test
    @DisplayName("versao em branco deixa os termos indisponiveis")
    void versaoEmBranco() throws IOException {
        escrever("termos.md", "versao:   \n\nTexto de teste.\n");
        escrever("privacidade.md", "versao:   \n\nTexto de teste.\n");

        assertThat(servico().atual()).isEmpty();
    }

    @Test
    @DisplayName("versoes diferentes entre os arquivos deixam os termos indisponiveis")
    void versoesDivergentes() throws IOException {
        escrever("termos.md", "versao: v1\n\nTexto de teste.\n");
        escrever("privacidade.md", "versao: v2\n\nTexto de teste.\n");

        assertThat(servico().atual()).isEmpty();
    }

    @Test
    @DisplayName("le os arquivos a cada chamada: publicar depois do boot basta")
    void leACadaChamada() throws IOException {
        var servico = servico();
        assertThat(servico.atual()).isEmpty();

        escrever("termos.md", "versao: v1\n\nTexto de teste.\n");
        escrever("privacidade.md", "versao: v1\n\nTexto de teste.\n");

        assertThat(servico.atual()).isPresent();
    }
}
