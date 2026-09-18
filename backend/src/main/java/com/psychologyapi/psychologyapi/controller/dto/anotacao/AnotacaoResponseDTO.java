package com.psychologyapi.psychologyapi.controller.dto.anotacao;

import java.time.LocalDateTime;
import java.util.UUID;

public record AnotacaoResponseDTO(

        UUID id,
        String conteudo,
        // F2 da revisao da Fatia 2: era LocalDate, e o mapper gerado chamava
        // .toLocalDate() — gravar a anotacao devolvia "2026-09-17" enquanto a
        // ficha devolvia "2026-09-17T14:03:11" para o MESMO registro. Num
        // prontuario a hora do lancamento faz parte do lancamento (spec 7.3).
        LocalDateTime createdAt,
        UUID pacienteId,
        UUID consultaId

) {}
