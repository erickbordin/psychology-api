package com.psychologyapi.psychologyapi.controller.dto.anotacao;

import jakarta.validation.constraints.NotBlank;

public record AnotacaoCadastroDTO(
        @NotBlank(message = "Informe o conteúdo da anotação.") String anotacao
) {}
