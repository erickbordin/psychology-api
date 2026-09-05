package com.psychologyapi.psychologyapi.controller.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.psychologyapi.psychologyapi.controller.dto.error.ErroRespostaDTO;

/**
 * I6 do laudo da revisao do conjunto (2026-09-05): estouro de limite ou
 * constraint do banco (PUT /pacientes com telefone maior que a coluna, ou
 * NOT NULL apagado numa atualizacao parcial) tem que virar 400, nao 500.
 *
 * Testado isolado do Hibernate/Postgres de proposito: o handler so precisa
 * do TIPO da excecao para decidir o status. Provar o fluxo inteiro via HTTP
 * (flush de verdade disparando a excecao dentro do request) esbarra em como
 * o @HttpTest isola os testes — o metodo @Transactional do service so
 * participa da transacao do teste, que so faz rollback no final, entao o
 * flush que dispararia a excecao nunca chega a acontecer contra o Postgres
 * dentro da janela do teste. Em producao, sem essa transacao de teste por
 * cima, o commit do @Transactional do service flusha normalmente e a
 * excecao chega aqui do mesmo jeito.
 */
class GlobalExceptionsHandlerTest {

    private final GlobalExceptionsHandler handler = new GlobalExceptionsHandler();

    @Test
    @DisplayName("DataIntegrityViolationException vira 400 com mensagem, nao 500")
    void dataIntegrityViolationViraQuatrocentos() {
        ErroRespostaDTO resposta = handler.handleDataIntegrityViolationException(
                new DataIntegrityViolationException("ERROR: value too long for type character varying(20)"));

        assertThat(resposta.status()).isEqualTo(400);
        assertThat(resposta.mensagem()).isNotBlank();
        assertThat(resposta.erros()).isEmpty();
    }
}
