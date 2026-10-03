package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record ValorDaConsultaDTO(
        @NotNull(message = "Informe o valor da sessão.")
        @DecimalMin(value = "0.01", message = "O valor da sessão deve ser maior que zero.")
        @Digits(integer = 8, fraction = 2, message = "O valor da sessão deve ter no máximo duas casas decimais.")
        @Schema(maximum = "99999999.99", multipleOf = 0.01,
                example = "180.00",
                description = "Valor desta sessão. Maior que zero, no máximo 8 dígitos "
                        + "inteiros e 2 casas decimais.")
        BigDecimal valor) {
}
