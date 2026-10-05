package com.psychologyapi.psychologyapi.exceptions;

public class CadastroIndisponivelException extends RuntimeException {

    public CadastroIndisponivelException(String mensagem) {
        super(mensagem);
    }

}
