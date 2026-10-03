package com.psychologyapi.psychologyapi.controller.dto.financeiro;

import java.math.BigDecimal;
import java.util.List;

public record FinanceiroMesDTO(
        int ano,
        int mes,
        BigDecimal faturado,
        BigDecimal recebido,
        BigDecimal aReceber,
        int sessoesSemValor,
        List<SessaoFinanceiraDTO> sessoes,
        List<DevedorDTO> devendo) {
}
