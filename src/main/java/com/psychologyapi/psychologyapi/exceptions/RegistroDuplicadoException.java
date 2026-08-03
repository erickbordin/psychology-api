package com.psychologyapi.psychologyapi.exceptions;

public class RegistroDuplicadoException extends RuntimeException{
    
    public RegistroDuplicadoException(String mensagem){
        super(mensagem);
    }

}
