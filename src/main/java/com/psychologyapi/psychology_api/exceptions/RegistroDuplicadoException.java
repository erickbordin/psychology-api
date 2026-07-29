package com.psychologyapi.psychology_api.exceptions;

public class RegistroDuplicadoException extends RuntimeException{
    
    public RegistroDuplicadoException(String mensagem){
        super(mensagem);
    }

}
