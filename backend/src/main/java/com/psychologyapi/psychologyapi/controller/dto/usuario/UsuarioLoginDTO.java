package com.psychologyapi.psychologyapi.controller.dto.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
public record UsuarioLoginDTO(
        @Email(message = "invalido") String emailUsuario,
        @NotBlank(message = "campo obrigatorio") String senha) {

}
