package com.psychologyapi.psychologyapi.service.aviso;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.psychologyapi.psychologyapi.model.Antecedencia;

class JanelaDeAvisoTest {

    private static final LocalDateTime SESSAO = LocalDateTime.of(2026, 12, 3, 14, 0);

    @Test
    void h24VinteEQuatroHorasAntes() {
        assertThat(JanelaDeAviso.momento(SESSAO, Antecedencia.H24))
                .isEqualTo(LocalDateTime.of(2026, 12, 2, 14, 0));
    }

    @Test
    void h2DuasHorasAntes() {
        assertThat(JanelaDeAviso.momento(SESSAO, Antecedencia.H2))
                .isEqualTo(LocalDateTime.of(2026, 12, 3, 12, 0));
    }

    @Test
    void vesperaAsDezoitoHorasDoDiaAnterior() {
        assertThat(JanelaDeAviso.momento(SESSAO, Antecedencia.VESPERA))
                .isEqualTo(LocalDateTime.of(2026, 12, 2, 18, 0));
    }

    @Test
    void vesperaDeSessaoDeManhaContinuaNoDiaAnteriorAsDezoito() {
        assertThat(JanelaDeAviso.momento(LocalDateTime.of(2026, 12, 3, 8, 0), Antecedencia.VESPERA))
                .isEqualTo(LocalDateTime.of(2026, 12, 2, 18, 0));
    }
}
