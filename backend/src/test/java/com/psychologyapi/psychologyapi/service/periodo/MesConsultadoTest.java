package com.psychologyapi.psychologyapi.service.periodo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.psychologyapi.psychologyapi.exceptions.PeriodoInvalidoException;

class MesConsultadoTest {

    private final ZoneId fuso = ZoneId.of("America/Sao_Paulo");
    private final Clock dezembro = Clock.fixed(
            LocalDateTime.parse("2026-12-02T10:00:00").atZone(fuso).toInstant(), fuso);

    @Test
    @DisplayName("sem ano nem mes, e o mes corrente do relogio")
    void semParametrosEOMesCorrente() {
        MesConsultado mes = MesConsultado.resolver(null, null, dezembro);
        assertThat(mes.ano()).isEqualTo(2026);
        assertThat(mes.mes()).isEqualTo(12);
    }

    @Test
    @DisplayName("o intervalo e semiaberto: do primeiro dia 00:00 ao primeiro dia do mes seguinte")
    void intervaloSemiaberto() {
        MesConsultado mes = MesConsultado.resolver(2026, 12, dezembro);
        assertThat(mes.inicio()).isEqualTo(LocalDateTime.parse("2026-12-01T00:00:00"));
        assertThat(mes.fimExclusivo()).isEqualTo(LocalDateTime.parse("2027-01-01T00:00:00"));
    }

    @Test
    @DisplayName("so o ano: 400 no campo mes")
    void soAno() {
        assertThatThrownBy(() -> MesConsultado.resolver(2026, null, dezembro))
                .isInstanceOf(PeriodoInvalidoException.class)
                .hasMessage("Informe 'ano' e 'mes' juntos, ou nenhum dos dois.")
                .extracting("campo").isEqualTo("mes");
    }

    @Test
    @DisplayName("so o mes: 400 no campo ano")
    void soMes() {
        assertThatThrownBy(() -> MesConsultado.resolver(null, 5, dezembro))
                .isInstanceOf(PeriodoInvalidoException.class)
                .extracting("campo").isEqualTo("ano");
    }

    @Test
    @DisplayName("mes 13 e mes 0 sao recusados no campo mes")
    void mesForaDaFaixa() {
        assertThatThrownBy(() -> MesConsultado.resolver(2026, 13, dezembro))
                .hasMessage("O mês deve estar entre 1 e 12.").extracting("campo").isEqualTo("mes");
        assertThatThrownBy(() -> MesConsultado.resolver(2026, 0, dezembro))
                .extracting("campo").isEqualTo("mes");
    }

    @Test
    @DisplayName("ano fora de 2000-2100 e recusado no campo ano")
    void anoForaDaFaixa() {
        assertThatThrownBy(() -> MesConsultado.resolver(1999, 1, dezembro))
                .hasMessage("O ano deve estar entre 2000 e 2100.").extracting("campo").isEqualTo("ano");
        assertThatThrownBy(() -> MesConsultado.resolver(2101, 1, dezembro))
                .extracting("campo").isEqualTo("ano");
    }
}
