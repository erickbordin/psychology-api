package com.psychologyapi.psychology_api.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
public record UsuarioLoginDTO(
        @Email(message = "invalido") String email_usuario,
        @NotBlank(message = "campo obrigatorio") String senha) {

}
