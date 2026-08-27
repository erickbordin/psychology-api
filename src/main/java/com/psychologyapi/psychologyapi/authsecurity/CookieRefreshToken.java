package com.psychologyapi.psychologyapi.authsecurity;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * O refresh viaja em cookie HttpOnly porque e a unica barreira que JavaScript nao
 * contorna — e o dado atras dele e anotacao de sessao de paciente.
 *
 * Path=/auth restringe o envio aos tres endpoints que precisam do cookie: nenhuma
 * chamada a /pacientes ou /consultas carrega a credencial de renovacao junto.
 */
@Component
public class CookieRefreshToken {

    public static final String NOME = "refresh_token";
    private static final String CAMINHO = "/auth";

    private final boolean seguro;
    private final String sameSite;
    private final int expiracaoDias;

    public CookieRefreshToken(
            @Value("${app.refresh.cookie-seguro}") boolean seguro,
            @Value("${app.refresh.cookie-samesite}") String sameSite,
            @Value("${app.refresh.expiracao-dias}") int expiracaoDias) {
        this.seguro = seguro;
        this.sameSite = sameSite;
        this.expiracaoDias = expiracaoDias;
    }

    public ResponseCookie emitir(String tokenEmClaro) {
        return base(tokenEmClaro).maxAge(Duration.ofDays(expiracaoDias)).build();
    }

    public ResponseCookie expirar() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(NOME, valor)
                .httpOnly(true)
                .secure(seguro)
                .sameSite(sameSite)
                .path(CAMINHO);
    }

}
