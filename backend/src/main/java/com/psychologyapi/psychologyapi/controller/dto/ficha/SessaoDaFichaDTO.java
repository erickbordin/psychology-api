package com.psychologyapi.psychologyapi.controller.dto.ficha;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.psychologyapi.psychologyapi.model.Status;

/**
 * Uma linha da linha do tempo: um encontro, com o que foi escrito nele.
 *
 * `anotacoes` e lista, e nao um campo unico, porque nada no modelo impede duas
 * anotacoes na mesma consulta — `Anotacao.consulta` e @ManyToOne sem unique.
 * Devolver so uma esconderia conteudo de prontuario em silencio, que e o pior
 * jeito de errar num registro com prazo de guarda legal (spec 7.3).
 *
 * `valor` e `pago` NAO entram aqui: sao da Fatia 3 (spec 9). Os campos ja
 * existem em Consulta; nao antecipar.
 */
public record SessaoDaFichaDTO(
        UUID consultaId,
        LocalDateTime dtConsulta,
        Status status,
        List<AnotacaoDaSessaoDTO> anotacoes) {
}
