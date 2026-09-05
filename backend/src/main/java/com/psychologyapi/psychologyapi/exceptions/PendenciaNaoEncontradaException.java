package com.psychologyapi.psychologyapi.exceptions;

public class PendenciaNaoEncontradaException extends RuntimeException{

    public PendenciaNaoEncontradaException(String mensagem){
        super(mensagem);
    }

}
