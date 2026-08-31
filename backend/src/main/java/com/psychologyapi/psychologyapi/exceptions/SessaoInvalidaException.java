package com.psychologyapi.psychologyapi.exceptions;

/**
 * Refresh ausente, desconhecido, expirado, revogado ou reapresentado.
 *
 * A mensagem e a mesma para todos os casos de proposito: distinguir "nunca
 * existiu" de "ja foi usado" so ajudaria quem esta sondando a API.
 */
public class SessaoInvalidaException extends RuntimeException {

    public SessaoInvalidaException(String mensagem) {
        super(mensagem);
    }

}
