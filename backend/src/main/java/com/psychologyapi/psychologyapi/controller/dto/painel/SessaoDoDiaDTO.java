package com.psychologyapi.psychologyapi.controller.dto.painel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.psychologyapi.psychologyapi.model.Status;

public record SessaoDoDiaDTO(
        UUID consultaId,
        LocalDateTime dtConsulta,
        UUID pacienteId,
        String pacienteNome,
        Status status,
        BigDecimal valor,
        LocalDateTime pagoEm) {
}
