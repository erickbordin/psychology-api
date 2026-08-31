package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.util.UUID;

public record SerieCancelamentoResponseDTO(

        UUID serieId,
        int ocorrenciasRemovidas

) {}
