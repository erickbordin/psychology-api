package com.psychologyapi.psychologyapi.controller.dto.paciente;

import com.psychologyapi.psychologyapi.model.CanalAviso;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Aceita os dois records de paciente sem interface comum: o sealed-por-contrato
 * aqui seria cerimonia: um `instanceof` por tipo e menor e mais obvio do que
 * uma interface criada so para dois records de DTO se parecerem.
 *
 * O erro e reportado NO CAMPO `email`, e nao na classe, porque o envelope de
 * erro da casa entrega "campo culpado" e o formulario poe o foco nele — erro
 * de classe chegaria sem campo e o foco nao teria onde pousar.
 */
public class EmailExigidoPeloCanalValidator implements ConstraintValidator<EmailExigidoPeloCanal, Object> {

    @Override
    public boolean isValid(Object dto, ConstraintValidatorContext context) {
        CanalAviso canal;
        String email;

        if (dto instanceof PacienteCadastroDTO cadastro) {
            canal = cadastro.canalAviso();
            email = cadastro.email();
        } else if (dto instanceof PacienteAtualizacaoDTO atualizacao) {
            canal = atualizacao.canalAviso();
            email = atualizacao.email();
        } else {
            return true;
        }

        if (canal != CanalAviso.EMAIL) {
            return true;
        }
        if (email != null && !email.isBlank()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
               .addPropertyNode("email")
               .addConstraintViolation();
        return false;
    }
}
