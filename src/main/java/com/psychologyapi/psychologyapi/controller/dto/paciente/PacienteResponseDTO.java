package com.psychologyapi.psychologyapi.controller.dto.paciente;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PacienteResponseDTO(

    UUID idPaciente,
    UUID idUsuario,
    String nome,
    String email,
    String telefone,
    LocalDate dataNascimento,
    LocalDateTime createdAt

) {}
