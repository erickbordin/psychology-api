package com.psychologyapi.psychologyapi.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AnotacaoCadastroDTO(
        @NotNull(message = "O paciente é obrigatório.") UUID pacienteId,
        @NotBlank(message = "campo obrigatorio") String anotacao
) {}
