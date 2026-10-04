package com.psychologyapi.psychologyapi.controller.dto.usuario;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CabeNoBcryptValidator implements ConstraintValidator<CabeNoBcrypt, String> {

    private static final int LIMITE_EM_BYTES = 72;

    @Override
    public boolean isValid(String senha, ConstraintValidatorContext contexto) {
        return senha == null || senha.getBytes(StandardCharsets.UTF_8).length <= LIMITE_EM_BYTES;
    }
}
