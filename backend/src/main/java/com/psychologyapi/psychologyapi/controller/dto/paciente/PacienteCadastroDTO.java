package com.psychologyapi.psychologyapi.controller.dto.paciente;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;

public record PacienteCadastroDTO(
        @NotBlank(message = "Informe o nome do paciente.") String nome,
        String telefone,
        String email,
        @Past(message = "A data de nascimento deve estar no passado.") LocalDate dataNascimento) {
}
