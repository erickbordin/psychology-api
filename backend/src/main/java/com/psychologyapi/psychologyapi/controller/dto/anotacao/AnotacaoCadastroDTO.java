package com.psychologyapi.psychologyapi.controller.dto.anotacao;

import jakarta.validation.constraints.NotBlank;

public record AnotacaoCadastroDTO(
        @NotBlank(message = "campo obrigatorio") String anotacao
) {}
