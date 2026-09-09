package com.psychologyapi.psychologyapi.controller.dto.painel;

import java.util.List;

/** A tela (1) inteira numa chamada (spec 5.6).
 *
 *  `proxima` e nula quando nao ha mais sessao hoje — inclusive no dia 1 do
 *  psicologo, que e um estado que a tela precisa saber desenhar.
 *
 *  NAO devolve "quanto falta" em minutos de proposito: seria um numero que
 *  nasce velho e envelhece na tela. O cliente calcula a partir de
 *  `dtConsulta`, que e um fato. */
public record PainelHojeDTO(
        SessaoDoDiaDTO proxima,
        List<SessaoDoDiaDTO> sessoes,
        List<PendenciaDoPainelDTO> pendencias) {
}
