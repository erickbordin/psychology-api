package com.psychologyapi.psychologyapi.controller.dto.financeiro;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DevedorDTO(
        UUID pacienteId,
        String pacienteNome,
        boolean pacienteExcluido,
        int quantidadeSessoes,
        BigDecimal total,
        List<UUID> consultaIds) {
}
