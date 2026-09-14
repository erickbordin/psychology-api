package com.psychologyapi.psychologyapi.controller.dto.paciente;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Spec 5.4: "E-mail continua opcional — exceto quando o canal e EMAIL, ai e
 * obrigatorio. Validacao condicional, nao @NotNull cego."
 *
 * Vai na CLASSE, e nao no campo, porque a regra le dois campos ao mesmo tempo:
 * constraint de campo nao enxerga o vizinho.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EmailExigidoPeloCanalValidator.class)
public @interface EmailExigidoPeloCanal {

    String message() default "Informe o e-mail do paciente para avisar por e-mail.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
