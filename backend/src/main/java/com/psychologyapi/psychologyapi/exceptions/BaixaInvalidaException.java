package com.psychologyapi.psychologyapi.exceptions;

public class BaixaInvalidaException extends RuntimeException {

    public static final String SO_REALIZADA_RECEBE_BAIXA =
            "Só sessão realizada recebe baixa. Marque a sessão como realizada antes.";
    public static final String SEM_VALOR =
            "Defina o valor antes de dar baixa.";
    public static final String SO_REALIZADA_TEM_VALOR =
            "Só sessão realizada tem valor a alterar. Marque a sessão como realizada antes.";

    public BaixaInvalidaException(String mensagem) {
        super(mensagem);
    }
}
