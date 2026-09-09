package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// Sem @Future de proposito: consulta no passado avisa mas nao bloqueia
// (spec 6.3) — o psicologo precisa registrar sessao que ja aconteceu.
public record ConsultaCadastroDTO(

    @NotNull(message = "O paciente é obrigatório.") UUID pacienteId,
    @NotNull(message = "A data da consulta é obrigatória.")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(type = "string", example = "2026-12-02T14:00:00",
            description = "Data e hora local do consultório, sem offset — o formato é "
                    + "yyyy-MM-dd'T'HH:mm:ss e um valor com offset (ex.: 'Z' ou '-03:00') "
                    + "é recusado, não convertido.")
    LocalDateTime dtConsulta

) {}
