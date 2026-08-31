package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

@HorizonteValido
public record ConsultaRecorrenteCadastroDTO(

        @NotNull(message = "O paciente é obrigatório.") UUID pacienteId,

        @NotNull(message = "A data da primeira consulta é obrigatória.")
        @Future(message = "A data da primeira consulta deve estar no futuro.")
        LocalDateTime dtPrimeiraConsulta,

        Integer quantidadeSessoes,

        LocalDate dtLimite

) {}
