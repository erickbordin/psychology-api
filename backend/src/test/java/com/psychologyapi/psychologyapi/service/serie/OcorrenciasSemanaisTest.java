package com.psychologyapi.psychologyapi.service.serie;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcorrenciasSemanaisTest {

    private static final LocalDateTime TERCA_15H = LocalDateTime.of(2026, 9, 1, 15, 0);

    @Test
    @DisplayName("aPartirDe gera a quantidade pedida, de 7 em 7 dias, na mesma hora")
    void geraQuantidadePedida() {
        List<LocalDateTime> ocorrencias = OcorrenciasSemanais.aPartirDe(TERCA_15H, 4);

        assertThat(ocorrencias).hasSize(4);
        assertThat(ocorrencias.get(0)).isEqualTo(TERCA_15H);
        assertThat(ocorrencias.get(3)).isEqualTo(TERCA_15H.plusWeeks(3));
        assertThat(ocorrencias).allMatch(dt -> dt.getDayOfWeek() == DayOfWeek.TUESDAY);
        assertThat(ocorrencias).allMatch(dt -> dt.toLocalTime().equals(TERCA_15H.toLocalTime()));
    }

    @Test
    @DisplayName("ate inclui a ocorrencia que cai no proprio dia do limite")
    void incluiOcorrenciaNoDiaDoLimite() {
        LocalDate limite = TERCA_15H.plusWeeks(2).toLocalDate();

        List<LocalDateTime> ocorrencias = OcorrenciasSemanais.ate(TERCA_15H, limite, 52);

        assertThat(ocorrencias).containsExactly(TERCA_15H, TERCA_15H.plusWeeks(1), TERCA_15H.plusWeeks(2));
    }

    @Test
    @DisplayName("ate nao passa do limite")
    void naoPassaDoLimite() {
        LocalDate limite = TERCA_15H.plusWeeks(2).minusDays(1).toLocalDate();

        List<LocalDateTime> ocorrencias = OcorrenciasSemanais.ate(TERCA_15H, limite, 52);

        assertThat(ocorrencias).containsExactly(TERCA_15H, TERCA_15H.plusWeeks(1));
    }

    @Test
    @DisplayName("ate para em teto + 1, para quem chama detectar o estouro sem gerar lista gigante")
    void paraEmTetoMaisUm() {
        LocalDate limiteDistante = TERCA_15H.plusYears(5).toLocalDate();

        List<LocalDateTime> ocorrencias = OcorrenciasSemanais.ate(TERCA_15H, limiteDistante, 52);

        assertThat(ocorrencias).hasSize(53);
    }

    @Test
    @DisplayName("os limites da serie sao 2 e 52")
    void limitesDaSerie() {
        assertThat(OcorrenciasSemanais.MIN_OCORRENCIAS).isEqualTo(2);
        assertThat(OcorrenciasSemanais.MAX_OCORRENCIAS).isEqualTo(52);
    }

    @Test
    @DisplayName("ate devolve lista vazia quando o limite e anterior a primeira ocorrencia")
    void limiteAnteriorDevolveVazio() {
        LocalDate limite = TERCA_15H.minusDays(1).toLocalDate();

        assertThat(OcorrenciasSemanais.ate(TERCA_15H, limite, 52)).isEmpty();
    }
}
