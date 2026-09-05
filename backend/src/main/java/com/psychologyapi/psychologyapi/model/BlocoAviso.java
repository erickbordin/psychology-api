package com.psychologyapi.psychologyapi.model;

/** As frases opcionais do corpo do aviso (spec 4.5). Duas outras frases sao
 *  obrigatorias e nao aparecem aqui: nao se desligam.
 *
 *  O texto de cada bloco NAO mora no banco — mora no codigo que monta o
 *  e-mail. Aqui fica so a escolha do psicologo. */
public enum BlocoAviso {
    REMARCAR,
    CHEGADA,
    DURACAO
}
