package com.psychologyapi.psychologyapi.controller.dto.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
public record UsuarioLoginDTO(
        @NotBlank(message = "Informe um e-mail.")
        @Email(message = "Informe um e-mail em formato válido.")
        String emailUsuario,
        @NotBlank(message = "Informe a senha.") String senha) {

}
