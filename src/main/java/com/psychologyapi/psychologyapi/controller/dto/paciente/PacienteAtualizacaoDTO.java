package com.psychologyapi.psychologyapi.controller.dto.paciente;

import java.time.LocalDate;

public record PacienteAtualizacaoDTO(
        String nome,
        String telefone,
        String email,
        LocalDate dataNascimento) {
}
