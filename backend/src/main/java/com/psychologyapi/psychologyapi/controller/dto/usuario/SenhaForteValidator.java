package com.psychologyapi.psychologyapi.controller.dto.usuario;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SenhaForteValidator implements ConstraintValidator<SenhaForte, String> {

    /** Carregada uma vez, na classe: sao poucas centenas de linhas e a lista
     *  nao muda em tempo de execucao. Ler o arquivo a cada cadastro seria
     *  I/O por requisicao sem nenhum ganho. */
    private static final Set<String> COMUNS = carregar();

    private static Set<String> carregar() {
        try (InputStream in = SenhaForteValidator.class.getResourceAsStream("/senhas-comuns.txt")) {
            if (in == null) {
                // falhar alto: o arquivo e versionado, entao sumir significa
                // build quebrado — nao "sem lista, aceita tudo"
                throw new IllegalStateException("senhas-comuns.txt nao encontrado no classpath");
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return r.lines()
                        .map(String::trim)
                        .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                        .map(l -> l.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean isValid(String senha, ConstraintValidatorContext contexto) {
        // nulo e vazio sao problema do @NotBlank; duas mensagens pelo mesmo
        // defeito e ruido
        if (senha == null || senha.isBlank()) {
            return true;
        }
        return !COMUNS.contains(senha.toLowerCase(Locale.ROOT));
    }
}
