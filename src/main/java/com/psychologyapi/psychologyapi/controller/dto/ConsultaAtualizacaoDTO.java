package com.psychologyapi.psychologyapi.controller.dto;

import com.psychologyapi.psychologyapi.model.Status;
import java.time.LocalDateTime;

public record ConsultaAtualizacaoDTO(
    LocalDateTime dtConsulta,
    Status status
) {}
