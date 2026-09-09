package com.psychologyapi.psychologyapi.controller.dto.consulta;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import com.psychologyapi.psychologyapi.model.Status;

/**
 * So aceita REALIZADA e FALTOU — nunca AGENDADA nem CANCELADA.
 *
 * O que isto faz: estreita a rota para os dois status de FECHAMENTO que a
 * spec 4① pede (marcar presenca/falta em um clique), fora do enum inteiro.
 *
 * O que isto NAO faz — e uma versao anterior deste comentario afirmava o
 * contrario, incorretamente: esta constraint NAO fecha a brecha de duplo
 * agendamento. Ela e uma constraint de CAMPO do DTO — estruturalmente nao
 * enxerga o status ATUAL da entidade — e REALIZADA/FALTOU sao, os dois,
 * != CANCELADA, entao restringir o destino nao impede sair de CANCELADA.
 * A brecha real (cancelar A libera o horario para B; reabrir A por este PATCH
 * sem checagem alguma deixa A e B ativas ao mesmo tempo) e fechada pela
 * guarda em ConsultaService.atualizarStatus, que olha o status ATUAL da
 * consulta antes de trocar — nao aqui. Ver o comentario la para o raciocinio
 * completo.
 *
 * NAO alargar de volta para o enum inteiro sem entender essa guarda primeiro.
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
