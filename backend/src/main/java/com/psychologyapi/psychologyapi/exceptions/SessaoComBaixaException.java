package com.psychologyapi.psychologyapi.exceptions;

public class SessaoComBaixaException extends RuntimeException {

    public static final String AO_MUDAR_STATUS =
            "Esta sessão já tem baixa. Desfaça a baixa antes de mudar o status.";
    public static final String AO_ALTERAR_VALOR =
            "Esta sessão já tem baixa. Desfaça a baixa antes de alterar o valor.";
    public static final String AO_EXCLUIR =
            "Esta sessão tem baixa registrada. Desfaça a baixa antes de excluir a sessão.";

    public SessaoComBaixaException(String mensagem) {
        super(mensagem);
    }
}
