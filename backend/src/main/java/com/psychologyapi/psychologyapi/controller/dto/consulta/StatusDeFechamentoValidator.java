package com.psychologyapi.psychologyapi.controller.dto.consulta;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import com.psychologyapi.psychologyapi.model.Status;

/**
 * So aceita REALIZADA e FALTOU — nunca AGENDADA nem CANCELADA.
 *
 * Existe por causa de uma sequencia real: cancelar a consulta A das 14h libera
 * o horario (todo caminho de escrita trata CANCELADA como vago); criar a
 * consulta B nas mesmas 14h entao funciona; e se este PATCH aceitasse
 * "AGENDADA", um terceiro passo "descancelaria" A sem rodar checagem de
 * conflito nenhuma — A e B ficariam as duas ativas no mesmo horario. Aceitar
 * so os dois status de fechamento (nada se move, entao nada pode conflitar)
 * elimina a brecha em vez de remendar com uma checagem de conflito numa rota
 * cujo proposito e nao mexer em nada. NAO alargar de volta para o enum
 * inteiro sem resolver esse cenario primeiro.
 */
public class StatusDeFechamentoValidator implements ConstraintValidator<StatusDeFechamento, Status> {

    @Override
    public boolean isValid(Status status, ConstraintValidatorContext contexto) {
        // nulo passa: e trabalho do @NotNull, que ja esta no campo. Duas
        // mensagens para o mesmo defeito e ruido para quem le a resposta
        if (status == null) {
            return true;
        }
        return status == Status.REALIZADA || status == Status.FALTOU;
    }
}
