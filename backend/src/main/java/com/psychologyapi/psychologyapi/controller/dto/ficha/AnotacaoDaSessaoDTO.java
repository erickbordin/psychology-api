package com.psychologyapi.psychologyapi.controller.dto.ficha;

import java.time.LocalDateTime;
import java.util.UUID;

/** A anotacao como ela aparece DENTRO da linha da sessao (spec 4 (3)).
 *  Sem pacienteId nem consultaId: a posicao na arvore ja diz os dois. */
public record AnotacaoDaSessaoDTO(
        UUID anotacaoId,
        String conteudo,
        LocalDateTime createdAt) {
}
