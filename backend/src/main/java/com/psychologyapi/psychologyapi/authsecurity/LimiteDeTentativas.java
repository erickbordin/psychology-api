package com.psychologyapi.psychologyapi.authsecurity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.psychologyapi.psychologyapi.controller.dto.error.ErroRespostaDTO;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LimiteDeTentativas extends OncePerRequestFilter {

    private static final Set<String> ROTAS = Set.of("/auth/login", "/auth/registrar", "/auth/refresh");
    private static final Duration JANELA = Duration.ofSeconds(60);
    private static final UrlPathHelper URL_PATH_HELPER = new UrlPathHelper();
    private static final String MENSAGEM = "Muitas tentativas. Tente de novo em alguns minutos.";

    private static final class Janela {
        private final Instant inicio;
        private int tentativas;

        private Janela(Instant inicio) {
            this.inicio = inicio;
        }
    }

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int limite;
    private final Map<String, Janela> janelas = new HashMap<>();

    public LimiteDeTentativas(ObjectMapper objectMapper, Clock clock,
            @Value("${app.limite.por-minuto:10}") int limite) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.limite = limite;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !ROTAS.contains(caminho(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!registrar(caminho(request) + "|" + request.getRemoteAddr())) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(),
                    new ErroRespostaDTO(HttpStatus.TOO_MANY_REQUESTS.value(), MENSAGEM, List.of()));
            return;
        }
        chain.doFilter(request, response);
    }

    private static String caminho(HttpServletRequest request) {
        String caminho = URL_PATH_HELPER.getPathWithinApplication(request);
        return caminho.length() > 1 && caminho.endsWith("/") ? caminho.substring(0, caminho.length() - 1) : caminho;
    }

    private synchronized boolean registrar(String chave) {
        Instant agora = clock.instant();
        janelas.values().removeIf(janela -> !agora.isBefore(janela.inicio.plus(JANELA)));
        Janela janela = janelas.computeIfAbsent(chave, k -> new Janela(agora));
        janela.tentativas++;
        return janela.tentativas <= limite;
    }

}
