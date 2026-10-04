package com.psychologyapi.psychologyapi.controller.dto.ajustes;

import java.math.BigDecimal;
import java.util.Set;

import com.psychologyapi.psychologyapi.model.Antecedencia;
import com.psychologyapi.psychologyapi.model.BlocoAviso;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AjustesAtualizacaoDTO(
        @NotBlank(message = "Informe seu nome.")
        @Size(min = 2, max = 100, message = "O nome deve ter entre 2 e 100 caracteres.")
        String nome,

        @DecimalMin(value = "0.01", message = "O valor da sessão deve ser maior que zero.")
        @Digits(integer = 8, fraction = 2, message = "O valor da sessão deve ter no máximo duas casas decimais.")
        @Schema(maximum = "99999999.99", multipleOf = 0.01,
                example = "180.00",
                description = "Valor padrão da sessão. Maior que zero, no máximo 8 dígitos "
                        + "inteiros e 2 casas decimais. Ausente limpa o valor.")
        BigDecimal valorPadraoSessao,

        @NotNull(message = "Escolha a antecedência do aviso.")
        Antecedencia antecedencia,

        @Schema(description = "Blocos incluídos no aviso. Ausente equivale a nenhum.")
        Set<BlocoAviso> blocosAviso,

        boolean tituloNoEmail,

        @Size(max = 80, message = "O título deve ter no máximo 80 caracteres.")
        String tituloProfissional,

        boolean resumoDiario,

        @Min(value = 0, message = "O início do expediente deve estar entre 0 e 23.")
        @Max(value = 23, message = "O início do expediente deve estar entre 0 e 23.")
        Integer expedienteInicio,

        @Min(value = 1, message = "O fim do expediente deve estar entre 1 e 24.")
        @Max(value = 24, message = "O fim do expediente deve estar entre 1 e 24.")
        Integer expedienteFim) {
}
