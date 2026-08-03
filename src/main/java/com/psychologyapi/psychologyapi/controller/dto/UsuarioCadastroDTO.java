package com.psychologyapi.psychologyapi.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UsuarioCadastroDTO(
        @NotBlank(message = "campo obrigatorio") String nome,
        @Email(message = "invalido") String email,
        @NotBlank(message = "campo obrigatorio") String senha) {

}
