package com.psychologyapi.psychologyapi.controller.dto.usuario;

import java.lang.annotation.*;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Recusa senha que aparece em lista de vazamento (spec 6.3). O tamanho
 *  minimo e outra regra, e fica no @Size — sao defeitos diferentes e o
 *  usuario merece saber qual dos dois cometeu. */
@Documented
@Constraint(validatedBy = SenhaForteValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface SenhaForte {
    String message() default "Essa senha é fácil de adivinhar. Escolha outra.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
