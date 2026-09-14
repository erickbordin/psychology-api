package com.psychologyapi.psychologyapi.controller.dto.anotacao;

import java.time.LocalDate;
import java.util.UUID;

public record AnotacaoResponseDTO(

        UUID id,
        String conteudo,
        LocalDate createdAt,
        UUID pacienteId,
        UUID consultaId

) {}
