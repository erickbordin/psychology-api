package com.psychologyapi.psychologyapi.controller.dto.paciente;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

public record PacienteCadastroDTO(
        @NotBlank(message = "campo obrigatorio") String nome,
        String telefone,
        String email,
        @NotNull(message = "A data de nascimento é obrigatória.") @Past(message = "A data de nascimento deve ser uma data válida.") LocalDate dataNascimento) {
}
