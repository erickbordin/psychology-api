package com.psychologyapi.psychologyapi.controller.dto.paciente;

import java.lang.annotation.*;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = TelefoneBRValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface TelefoneBR {
    String message() default "Informe um telefone brasileiro válido, com DDD.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
