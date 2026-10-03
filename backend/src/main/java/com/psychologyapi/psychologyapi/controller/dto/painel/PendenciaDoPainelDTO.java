package com.psychologyapi.psychologyapi.controller.dto.painel;

import java.time.LocalDateTime;
import java.util.UUID;

/** Uma linha da fila de pendencias da tela (1). O `tipo` diz qual das especies
 *  e. */
public record PendenciaDoPainelDTO(
        UUID consultaId,
        LocalDateTime dtConsulta,
        String pacienteNome,
        TipoPendencia tipo) {
}
