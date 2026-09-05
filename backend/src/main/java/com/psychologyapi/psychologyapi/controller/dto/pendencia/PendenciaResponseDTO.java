package com.psychologyapi.psychologyapi.controller.dto.pendencia;

import java.time.LocalDateTime;
import java.util.UUID;

public record PendenciaResponseDTO(

    UUID id,
    String descricao,
    Boolean concluido,
    LocalDateTime createdAt

) {}
