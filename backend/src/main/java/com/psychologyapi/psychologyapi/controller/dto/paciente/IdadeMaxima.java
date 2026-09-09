package com.psychologyapi.psychologyapi.controller.dto.paciente;

import java.lang.annotation.*;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Idade impossivel e quase sempre erro de digitacao no ano (1925 no lugar
 *  de 1995). @Past sozinho aceita 1825 sem reclamar. */
@Documented
@Constraint(validatedBy = IdadeMaximaValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface IdadeMaxima {
    int anos();
    String message() default "Confira a data de nascimento.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
