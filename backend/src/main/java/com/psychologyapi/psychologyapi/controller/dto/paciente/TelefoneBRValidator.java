package com.psychologyapi.psychologyapi.controller.dto.paciente;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Valida a CONTAGEM DE DIGITOS, nao o desenho da mascara (spec 6.3).
 *
 *  A primeira versao desta regra era uma @Pattern que policiava pontuacao, e
 *  ela recusava "(51) 99811-2345 " — um espaco colado no fim, acidente de
 *  copiar e colar — enquanto aceitava "(5199811234", com parentese
 *  desbalanceado. Policiar pontuacao recusa numero valido e aceita numero
 *  torto, que e o oposto do que se queria. O campo existe para ser canal de
 *  contato: o que importa e se os digitos formam um telefone brasileiro. */
public class TelefoneBRValidator implements ConstraintValidator<TelefoneBR, String> {

    @Override
    public boolean isValid(String telefone, ConstraintValidatorContext contexto) {
        // nulo e vazio passam: telefone e opcional, e quem nao tem cadastra
        // assim mesmo. Campo em branco tambem passa — @NotBlank e outra regra,
        // e aqui ela nao se aplica
        if (telefone == null || telefone.isBlank()) {
            return true;
        }

        String digitos = telefone.replaceAll("\\D", "");

        // 10 digitos: fixo com DDD. 11: celular com DDD e o nono digito.
        // 12 e 13: os mesmos dois com o 55 do pais na frente
        return switch (digitos.length()) {
            case 10, 11 -> true;
            case 12, 13 -> digitos.startsWith("55");
            default -> false;
        };
    }
}
