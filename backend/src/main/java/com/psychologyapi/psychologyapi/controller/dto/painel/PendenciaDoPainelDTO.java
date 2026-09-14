package com.psychologyapi.psychologyapi.controller.dto.painel;

import java.time.LocalDateTime;
import java.util.UUID;

/** Uma linha da fila de pendencias da tela (1). O `tipo` diz qual das especies
 *  e — "realizada sem baixa" chega na Fatia 3 (spec 9). */
public record PendenciaDoPainelDTO(
        UUID consultaId,
        LocalDateTime dtConsulta,
        String pacienteNome,
        TipoPendencia tipo) {
}
