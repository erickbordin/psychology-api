package com.psychologyapi.psychologyapi.controller.dto.auditoria;

import java.time.LocalDateTime;
import java.util.UUID;

import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;

public record LogAuditoriaResponseDTO(

        UUID id,
        AcaoAuditoria acao,
        String entidade,
        UUID entidadeId,
        LocalDateTime createdAt,
        UUID pacienteId,
        String pacienteNome,
        Boolean pacienteExcluido

) {}
