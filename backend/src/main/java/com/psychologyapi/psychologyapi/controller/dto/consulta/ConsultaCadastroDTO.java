package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

// Sem @Future de proposito: consulta no passado avisa mas nao bloqueia
// (spec 6.3) — o psicologo precisa registrar sessao que ja aconteceu.
public record ConsultaCadastroDTO(

    @NotNull(message = "O paciente é obrigatório.") UUID pacienteId,
    @NotNull(message = "A data da consulta é obrigatória.")
    LocalDateTime dtConsulta

) {}
