package com.psychologyapi.psychologyapi.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record LembreteCadastroDTO (
    @NotBlank(message = "campo obrigatorio") String anotacao
){}
