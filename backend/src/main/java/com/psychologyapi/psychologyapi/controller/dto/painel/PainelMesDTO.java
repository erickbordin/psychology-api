package com.psychologyapi.psychologyapi.controller.dto.painel;

import java.math.BigDecimal;

public record PainelMesDTO(
        int ano,
        int mes,
        long realizadas,
        long faltas,
        long canceladas,
        BigDecimal taxaDeFalta,
        long pacientesAtivos) {
}
