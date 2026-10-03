package com.psychologyapi.psychologyapi.controller.dto.financeiro;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SessaoFinanceiraDTO(
        UUID consultaId,
        LocalDateTime dtConsulta,
        UUID pacienteId,
        String pacienteNome,
        boolean pacienteExcluido,
        BigDecimal valor,
        LocalDateTime pagoEm) {
}
