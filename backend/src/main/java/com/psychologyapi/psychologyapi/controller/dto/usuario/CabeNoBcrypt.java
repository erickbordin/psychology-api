package com.psychologyapi.psychologyapi.controller.dto.usuario;

import java.lang.annotation.*;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = CabeNoBcryptValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface CabeNoBcrypt {
    String message() default "A senha deve ter no máximo 64 caracteres.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
