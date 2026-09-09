package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.lang.annotation.*;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Restringe PATCH /consultas/{id}/status aos dois status que a spec 4①
 *  realmente pede — ver {@link StatusDeFechamentoValidator} para o motivo. */
@Documented
@Constraint(validatedBy = StatusDeFechamentoValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface StatusDeFechamento {
    String message() default "Por aqui só é possível marcar a sessão como realizada ou faltou.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
