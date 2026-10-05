package com.psychologyapi.psychologyapi.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
public class TermosService {

    private static final String PREFIXO_VERSAO = "versao:";
    private static final int TAMANHO_MAXIMO_VERSAO = 20;

    private final ResourceLoader resourceLoader;
    private final String diretorio;

    public TermosService(ResourceLoader resourceLoader,
            @Value("${app.termos.diretorio:classpath:termos/}") String diretorio) {
        this.resourceLoader = resourceLoader;
        this.diretorio = diretorio.endsWith("/") ? diretorio : diretorio + "/";
    }

    public record TermosAtuais(String versao, String termos, String privacidade) {
    }

    private record Documento(String versao, String texto) {
    }

    public Optional<TermosAtuais> atual() {
        Optional<Documento> termos = ler("termos.md");
        Optional<Documento> privacidade = ler("privacidade.md");
        if (termos.isEmpty() || privacidade.isEmpty()) {
            return Optional.empty();
        }
        if (!termos.get().versao().equals(privacidade.get().versao())) {
            return Optional.empty();
        }
        return Optional.of(new TermosAtuais(termos.get().versao(), termos.get().texto(), privacidade.get().texto()));
    }

    private Optional<Documento> ler(String arquivo) {
        Resource recurso = resourceLoader.getResource(diretorio + arquivo);
        if (!recurso.exists()) {
            return Optional.empty();
        }
        String conteudo;
        try (InputStream entrada = recurso.getInputStream()) {
            conteudo = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Optional.empty();
        }
        int quebra = conteudo.indexOf('\n');
        String primeiraLinha = (quebra < 0 ? conteudo : conteudo.substring(0, quebra)).strip();
        if (!primeiraLinha.startsWith(PREFIXO_VERSAO)) {
            return Optional.empty();
        }
        String versao = primeiraLinha.substring(PREFIXO_VERSAO.length()).strip();
        if (versao.isEmpty() || versao.length() > TAMANHO_MAXIMO_VERSAO) {
            return Optional.empty();
        }
        String texto = quebra < 0 ? "" : conteudo.substring(quebra + 1).strip();
        return Optional.of(new Documento(versao, texto));
    }

}
