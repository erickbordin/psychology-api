package com.psychologyapi.psychologyapi.controller.dto.ajustes;

import java.math.BigDecimal;
import java.util.Set;

import com.psychologyapi.psychologyapi.model.Antecedencia;
import com.psychologyapi.psychologyapi.model.BlocoAviso;

public record AjustesResponseDTO(
        String nome,
        String email,
        BigDecimal valorPadraoSessao,
        Antecedencia antecedencia,
        Set<BlocoAviso> blocosAviso,
        boolean tituloNoEmail,
        String tituloProfissional,
        boolean resumoDiario,
        Integer expedienteInicio,
        Integer expedienteFim) {
}
