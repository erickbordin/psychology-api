package com.psychologyapi.psychologyapi.controller.dto.consulta;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.psychologyapi.psychologyapi.model.Status;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record ConsultaAtualizacaoDTO(
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(type = "string", example = "2026-12-02T14:00:00",
            description = "Data e hora local do consultório, sem offset — o formato é "
                    + "yyyy-MM-dd'T'HH:mm:ss e um valor com offset (ex.: 'Z' ou '-03:00') "
                    + "é recusado, não convertido.")
    LocalDateTime dtConsulta,
    Status status
) {}
