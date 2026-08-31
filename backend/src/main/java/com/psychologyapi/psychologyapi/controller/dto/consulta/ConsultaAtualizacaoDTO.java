package com.psychologyapi.psychologyapi.controller.dto.consulta;

import com.psychologyapi.psychologyapi.model.Status;
import java.time.LocalDateTime;

public record ConsultaAtualizacaoDTO(
    LocalDateTime dtConsulta,
    Status status
) {}
