package com.psychologyapi.psychologyapi.exceptions;

/**
 * Intervalo de datas invalido informado pelo cliente (de/ate parcial ou
 * invertido) em um filtro de listagem. Carrega o campo em que o erro deve
 * ser reportado para reaproveitar o mesmo formato de resposta 400 usado
 * pela validacao de bean (erros: [{campo, erro}]).
 */
public class PeriodoInvalidoException extends RuntimeException {

    private final String campo;

    public PeriodoInvalidoException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
