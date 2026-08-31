package com.psychologyapi.psychologyapi.exceptions;

public class ConsultaNaoEncontradaException extends RuntimeException {
    
    public ConsultaNaoEncontradaException(String mensagem){
        super(mensagem);
    }

}
