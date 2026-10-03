package com.psychologyapi.psychologyapi.controller.dto.painel;

/**
 * Que especie de pendencia e esta. O painel cresce por fatia (spec 9): a
 * Fatia 1 so tinha SEM_STATUS, a Fatia 2 traz SEM_ANOTACAO.
 * O tipo existe porque a tela escreve uma frase diferente
 * e leva a um lugar diferente em cada caso.
 */
public enum TipoPendencia {

    /** A hora passou e a sessao continua AGENDADA: ninguem disse o que houve. */
    SEM_STATUS,

    /** A sessao foi dada como REALIZADA e nao tem anotacao presa a ela. */
    SEM_ANOTACAO,

    SEM_BAIXA
}
