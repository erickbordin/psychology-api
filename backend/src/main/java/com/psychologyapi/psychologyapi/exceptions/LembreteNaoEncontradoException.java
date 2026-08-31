package com.psychologyapi.psychologyapi.exceptions;

public class LembreteNaoEncontradoException extends RuntimeException{
    
    public LembreteNaoEncontradoException(String mensagem){
        super(mensagem);
    }

}
