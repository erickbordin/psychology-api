package com.psychologyapi.psychologyapi.exceptions;

public class PacienteNaoEncontradoException extends RuntimeException{
    
    public PacienteNaoEncontradoException(String mensagem){
        super(mensagem);
    }

}
