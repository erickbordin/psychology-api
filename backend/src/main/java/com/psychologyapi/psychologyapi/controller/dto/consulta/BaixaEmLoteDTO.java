package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BaixaEmLoteDTO(
        @NotEmpty(message = "Informe ao menos uma sessão.")
        @Size(max = 200, message = "Informe no máximo 200 sessões por vez.")
        List<@NotNull(message = "Informe o id da sessão.") UUID> consultaIds) {
}
