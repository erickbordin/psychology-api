package com.psychologyapi.psychologyapi.service.aviso;

import java.time.LocalDateTime;

import com.psychologyapi.psychologyapi.model.Antecedencia;

public final class JanelaDeAviso {

    private JanelaDeAviso() {
    }

    public static LocalDateTime momento(LocalDateTime dtConsulta, Antecedencia antecedencia) {
        return switch (antecedencia) {
            case H24 -> dtConsulta.minusHours(24);
            case H2 -> dtConsulta.minusHours(2);
            case VESPERA -> dtConsulta.toLocalDate().minusDays(1).atTime(18, 0);
        };
    }
}
