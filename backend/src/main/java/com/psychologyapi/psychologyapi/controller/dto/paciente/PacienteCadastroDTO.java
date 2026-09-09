package com.psychologyapi.psychologyapi.controller.dto.paciente;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

public record PacienteCadastroDTO(
        @NotBlank(message = "Informe o nome do paciente.")
        @Size(min = 2, max = 100, message = "O nome deve ter de 2 a 100 caracteres.")
        String nome,

        // formato brasileiro, com ou sem mascara: (51) 99811-2345, 51998112345,
        // +55 51 99811-2345. Opcional — quem nao tem telefone cadastra assim mesmo.
        // @TelefoneBR conta digitos, nao policia mascara — @Size continua
        // porque muitos separadores podem estourar a coluna mesmo com os
        // digitos certos
        @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres.")
        @TelefoneBR
        String telefone,

        // vira canal de aviso (spec 5.4): endereco invalido aqui e falha de
        // envio silenciosa depois
        @Size(max = 100, message = "O e-mail deve ter no máximo 100 caracteres.")
        @Email(message = "Informe um e-mail em formato válido.")
        String email,

        @Past(message = "A data de nascimento deve estar no passado.")
        @IdadeMaxima(anos = 120, message = "Confira a data de nascimento.")
        LocalDate dataNascimento) {
}
