package com.psychologyapi.psychologyapi.exceptions;

/** A consulta existe e e do psicologo logado, mas pertence a outro paciente:
 *  prender a anotacao a ela misturaria dois prontuarios (spec 5.1). */
public class SessaoDeOutroPacienteException extends RuntimeException {

    public SessaoDeOutroPacienteException(String mensagem) {
        super(mensagem);
    }
}
