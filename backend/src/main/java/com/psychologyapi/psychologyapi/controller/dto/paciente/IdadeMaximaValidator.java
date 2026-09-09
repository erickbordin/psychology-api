package com.psychologyapi.psychologyapi.controller.dto.paciente;

import java.time.LocalDate;
import java.time.Period;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IdadeMaximaValidator implements ConstraintValidator<IdadeMaxima, LocalDate> {

    private int anos;

    @Override
    public void initialize(IdadeMaxima anotacao) {
        this.anos = anotacao.anos();
    }

    @Override
    public boolean isValid(LocalDate nascimento, ConstraintValidatorContext contexto) {
        // nulo e valido: a data de nascimento e OPCIONAL por decisao do dono
        // (spec 5.8). Data futura e problema do @Past, nao deste validador
        if (nascimento == null || nascimento.isAfter(LocalDate.now())) {
            return true;
        }
        return Period.between(nascimento, LocalDate.now()).getYears() <= anos;
    }
}
