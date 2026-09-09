package com.psychologyapi.psychologyapi.controller.dto.consulta;

import com.psychologyapi.psychologyapi.model.Status;
import jakarta.validation.constraints.NotNull;

/** Corpo de PATCH /consultas/{id}/status — so o status, de proposito.
 *  Mandar a data junto para trocar so o status faz o front reenviar dado
 *  que ele nao esta mudando, e e assim que campo se apaga sem querer. */
public record StatusConsultaDTO(
        @NotNull(message = "Informe o status da consulta.")
        @StatusDeFechamento Status status) {
}
