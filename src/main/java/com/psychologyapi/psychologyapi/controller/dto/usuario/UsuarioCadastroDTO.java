package com.psychologyapi.psychologyapi.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UsuarioCadastroDTO(
        @NotBlank(message = "campo obrigatorio") String nome,
        @NotBlank(message = "campo obrigatorio") @Email(message = "formato de email invalido") String email,
        @NotBlank(message = "campo obrigatorio") String senha) {

}
