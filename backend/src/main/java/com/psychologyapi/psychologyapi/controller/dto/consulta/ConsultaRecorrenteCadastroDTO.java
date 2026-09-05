package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

// Sem @Future de proposito: consulta no passado avisa mas nao bloqueia
// (spec 6.3) — o psicologo precisa registrar sessao que ja aconteceu.
@HorizonteValido
public record ConsultaRecorrenteCadastroDTO(

        @NotNull(message = "O paciente é obrigatório.") UUID pacienteId,

        @NotNull(message = "A data da primeira consulta é obrigatória.")
        LocalDateTime dtPrimeiraConsulta,

        Integer quantidadeSessoes,

        LocalDate dtLimite

) {}
