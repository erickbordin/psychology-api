package com.psychologyapi.psychologyapi.authsecurity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class CookieRefreshTokenTest {

    private final CookieRefreshToken cookie = new CookieRefreshToken(true, "None", 14, "/auth");

    @Test
    @DisplayName("o cookie emitido e httpOnly, seguro e restrito a /auth")
    void cookieEmitidoTemOsAtributosDeProtecao() {
        ResponseCookie emitido = cookie.emitir("token-em-claro");

        assertThat(emitido.getName()).isEqualTo(CookieRefreshToken.NOME);
        assertThat(emitido.getValue()).isEqualTo("token-em-claro");
        assertThat(emitido.isHttpOnly()).isTrue();
        assertThat(emitido.isSecure()).isTrue();
        assertThat(emitido.getSameSite()).isEqualTo("None");
        assertThat(emitido.getPath()).isEqualTo("/auth");
        assertThat(emitido.getMaxAge()).isEqualTo(Duration.ofDays(14));
    }

    @Test
    @DisplayName("o cookie de expiracao zera valor e prazo")
    void cookieDeExpiracaoZera() {
        ResponseCookie expirado = cookie.expirar();

        assertThat(expirado.getValue()).isEmpty();
        assertThat(expirado.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(expirado.getPath()).isEqualTo("/auth");
    }
}
