package com.psychologyapi.psychologyapi.controller.dto.lembrete;

import jakarta.validation.constraints.NotBlank;

public record LembreteCadastroDTO (
    @NotBlank(message = "campo obrigatorio") String descricao
){}
