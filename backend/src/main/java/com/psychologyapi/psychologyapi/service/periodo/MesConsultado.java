package com.psychologyapi.psychologyapi.service.periodo;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;

import com.psychologyapi.psychologyapi.exceptions.PeriodoInvalidoException;

public record MesConsultado(int ano, int mes, LocalDateTime inicio, LocalDateTime fimExclusivo) {

    public static MesConsultado resolver(Integer ano, Integer mes, Clock clock) {
        if (ano == null && mes == null) {
            return de(YearMonth.now(clock));
        }
        if (ano == null || mes == null) {
            throw new PeriodoInvalidoException(ano == null ? "ano" : "mes",
                    "Informe 'ano' e 'mes' juntos, ou nenhum dos dois.");
        }
        if (mes < 1 || mes > 12) {
            throw new PeriodoInvalidoException("mes", "O mês deve estar entre 1 e 12.");
        }
        if (ano < 2000 || ano > 2100) {
            throw new PeriodoInvalidoException("ano", "O ano deve estar entre 2000 e 2100.");
        }
        return de(YearMonth.of(ano, mes));
    }

    private static MesConsultado de(YearMonth mes) {
        return new MesConsultado(mes.getYear(), mes.getMonthValue(),
                mes.atDay(1).atStartOfDay(), mes.plusMonths(1).atDay(1).atStartOfDay());
    }
}
