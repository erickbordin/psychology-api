package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import com.psychologyapi.psychologyapi.model.Status;

public record ConsultaResponseDTO(
    UUID id,
    UUID pacienteId,
    String pacienteNome,
    LocalDateTime dtConsulta,
    Status status,
    LocalDateTime createdAt,
    UUID serieId,
    BigDecimal valor,
    LocalDateTime pagoEm
) {}