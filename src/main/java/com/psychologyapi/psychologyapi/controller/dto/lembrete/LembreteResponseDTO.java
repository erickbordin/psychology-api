package com.psychologyapi.psychologyapi.controller.dto.lembrete;

import java.time.LocalDateTime;
import java.util.UUID;

public record LembreteResponseDTO( 
    
    UUID id,
    String descricao,
    Boolean concluido,
    LocalDateTime createdAt

) {}
