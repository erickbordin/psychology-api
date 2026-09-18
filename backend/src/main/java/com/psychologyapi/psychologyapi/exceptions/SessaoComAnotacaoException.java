package com.psychologyapi.psychologyapi.exceptions;

/**
 * A sessao tem anotacao viva presa a ela, entao nao pode ser excluida (decisao
 * do dono, revisao da Fatia 2).
 *
 * O motivo e de prontuario, nao de integridade referencial: a exclusao logica
 * da consulta nunca apagou a anotacao, mas a ficha ({@code FichaService}) so
 * lista consulta nao apagada — o conteudo clinico continuava no banco e sumia
 * da vista, calado. Registro com prazo legal de guarda (spec 7.3) nao pode
 * desaparecer do prontuario por efeito colateral da exclusao de outra coisa.
 *
 * A saida fica com quem escreveu: apague a anotacao primeiro, deliberadamente,
 * e a sessao volta a ser excluivel.
 */
public class SessaoComAnotacaoException extends RuntimeException {

    public SessaoComAnotacaoException(String mensagem) {
        super(mensagem);
    }
}
