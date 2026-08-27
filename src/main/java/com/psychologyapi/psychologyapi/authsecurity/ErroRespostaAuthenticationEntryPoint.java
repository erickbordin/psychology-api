package com.psychologyapi.psychologyapi.authsecurity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.psychologyapi.psychologyapi.controller.dto.error.ErroRespostaDTO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

/**
 * O 401 da cadeia de seguranca — requisicao sem token, com token expirado ou
 * com token invalido — e emitido antes do DispatcherServlet, onde o
 * @RestControllerAdvice nao alcanca. Sem este entry point o corpo sairia vazio,
 * e o cliente teria duas formas de 401 para tratar: o envelope do login com
 * credencial invalida e o nada da cadeia.
 *
 * A mensagem e deliberadamente unica para os tres casos: distinguir "token
 * ausente" de "token expirado" de "assinatura invalida" so ajudaria quem esta
 * sondando a API.
 */
@Component
@RequiredArgsConstructor
public class ErroRespostaAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ErroRespostaDTO corpo = new ErroRespostaDTO(
                HttpStatus.UNAUTHORIZED.value(),
                "Token ausente, inválido ou expirado.",
                List.of());

        objectMapper.writeValue(response.getWriter(), corpo);
    }

}
