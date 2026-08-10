package com.psychologyapi.psychologyapi.controller.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

public record ConsultaCadastroDTO(

    @NotNull(message = "O paciente é obrigatório.") UUID pacienteId,
    @NotNull(message = "A data da consulta é obrigatória.")
    @Future(message = "A data da consulta deve estar no futuro.")
    LocalDateTime dtConsulta
    
) {}
