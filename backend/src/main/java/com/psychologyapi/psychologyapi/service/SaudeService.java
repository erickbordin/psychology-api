package com.psychologyapi.psychologyapi.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SaudeService {

    public enum Backup {
        OK("ok"), DESLIGADO("desligado"), ATRASADO("atrasado");

        private final String rotulo;

        Backup(String rotulo) {
            this.rotulo = rotulo;
        }

        public String rotulo() {
            return rotulo;
        }
    }

    private static final Duration LIMITE_DO_DUMP = Duration.ofHours(36);

    private final Clock clock;
    private final String arquivoDeEstado;

    public SaudeService(Clock clock, @Value("${app.backup.arquivo-de-estado:}") String arquivoDeEstado) {
        this.clock = clock;
        this.arquivoDeEstado = arquivoDeEstado;
    }

    public Backup estado() {
        if (arquivoDeEstado == null || arquivoDeEstado.isBlank()) {
            return Backup.DESLIGADO;
        }
        try {
            Map<String, String> chaves = ler(Path.of(arquivoDeEstado));
            boolean semFalha = chaves.getOrDefault("falha", "").isBlank();
            boolean testeOk = !"false".equals(chaves.get("ultimo_teste_ok"));
            LocalDateTime dump = LocalDateTime.parse(chaves.getOrDefault("ultimo_dump_ok", "").trim());
            boolean recente = !dump.isBefore(LocalDateTime.now(clock).minus(LIMITE_DO_DUMP));
            return semFalha && testeOk && recente ? Backup.OK : Backup.ATRASADO;
        } catch (IOException | RuntimeException e) {
            return Backup.ATRASADO;
        }
    }

    private Map<String, String> ler(Path arquivo) throws IOException {
        Map<String, String> chaves = new HashMap<>();
        for (String linha : Files.readAllLines(arquivo)) {
            String[] partes = linha.split("=", 2);
            if (partes.length == 2) {
                chaves.put(partes[0].trim(), partes[1].trim());
            }
        }
        return chaves;
    }
}
