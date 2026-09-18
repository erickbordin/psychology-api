package com.psychologyapi.psychologyapi.controller.dto.paciente;

import com.psychologyapi.psychologyapi.model.CanalAviso;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Spec 5.4: canal EMAIL exige e-mail. Aqui, so no CADASTRO.
 *
 * <h2>Por que o PUT saiu daqui (revisao da Fatia 2)</h2>
 *
 * Este validador le o PAYLOAD, e no cadastro isso e o certo: o payload e o
 * estado inteiro do paciente que esta nascendo. No PUT parcial ele estava
 * errado por construcao — {@code {"canalAviso":"EMAIL"}} sozinho chega sem
 * e-mail no corpo e era recusado com 400 mesmo quando o paciente ja tinha
 * e-mail salvo. Um 400 falso num caminho legitimo: trocar o canal de aviso de
 * quem ja tem contato por e-mail.
 *
 * A regra da atualizacao mudou para o {@code PacienteService}, onde o estado
 * EFETIVO (payload sobre entidade) esta disponivel, e sai como
 * {@code EmailExigidoPeloCanalException} — no mesmo envelope de 400 com campo
 * "email" que este validador produz, para o cliente ver uma resposta so.
 *
 * O tipo continua {@code Object} (e nao {@code PacienteCadastroDTO}) para a
 * anotacao seguir aplicavel a qualquer DTO futuro de criacao de paciente sem
 * trocar a assinatura; o que sobra fora do {@code instanceof} passa.
 *
 * O erro e reportado NO CAMPO {@code email}, e nao na classe, porque o envelope
 * de erro da casa entrega "campo culpado" e o formulario poe o foco nele — erro
 * de classe chegaria sem campo e o foco nao teria onde pousar.
 */
public class EmailExigidoPeloCanalValidator implements ConstraintValidator<EmailExigidoPeloCanal, Object> {

    @Override
    public boolean isValid(Object dto, ConstraintValidatorContext context) {
        if (!(dto instanceof PacienteCadastroDTO cadastro)) {
            return true;
        }

        if (cadastro.canalAviso() != CanalAviso.EMAIL) {
            return true;
        }
        if (cadastro.email() != null && !cadastro.email().isBlank()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
               .addPropertyNode("email")
               .addConstraintViolation();
        return false;
    }
}
