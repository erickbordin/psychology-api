package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.util.List;
import java.util.UUID;

public record SerieConsultaResponseDTO(

        UUID serieId,
        int quantidade,
        List<ConsultaResponseDTO> consultas

) {}
