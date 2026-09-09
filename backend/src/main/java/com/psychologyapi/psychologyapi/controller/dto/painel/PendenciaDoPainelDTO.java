package com.psychologyapi.psychologyapi.controller.dto.painel;

import java.time.LocalDateTime;
import java.util.UUID;

/** Sessao cuja hora ja passou e que continua AGENDADA (spec 4 (1)).
 *  As outras duas pendencias da tela final — realizada sem anotacao, e
 *  realizada sem baixa — sao das Fatias 2 e 3. Nao antecipar. */
public record PendenciaDoPainelDTO(
        UUID consultaId,
        LocalDateTime dtConsulta,
        String pacienteNome) {
}
