package com.psychologyapi.psychologyapi.controller.dto.pendencia;

import jakarta.validation.constraints.NotBlank;

public record PendenciaCadastroDTO (
    @NotBlank(message = "Informe a descrição da pendência.") String descricao
){}
