package com.psychologyapi.psychologyapi.controller.dto.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioCadastroDTO(
        @NotBlank(message = "Informe o nome.") String nome,
        @NotBlank(message = "Informe um e-mail.") @Email(message = "Informe um e-mail em formato válido.") String email,
        @NotBlank(message = "Informe uma senha.")
        @Size(min = 10, message = "A senha precisa de pelo menos 10 caracteres.")
        @SenhaForte
        String senha) {

}
