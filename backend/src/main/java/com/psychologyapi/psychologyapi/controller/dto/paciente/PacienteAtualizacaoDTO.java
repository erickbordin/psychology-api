package com.psychologyapi.psychologyapi.controller.dto.paciente;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.psychologyapi.psychologyapi.model.CanalAviso;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

// Sem @NotBlank de proposito: atualizacao e parcial, e a semantica de campo
// ausente apagar o que ja estava salvo e decisao de produto ainda em aberto
// (I6 do laudo da revisao do conjunto, 2026-09-05). Os @Size abaixo so
// espelham o limite que ja existe na coluna do banco, para que estourar o
// limite vire 400 na validacao, nao 500 na constraint.
@EmailExigidoPeloCanal
public record PacienteAtualizacaoDTO(
        @Size(min = 2, max = 100, message = "O nome deve ter de 2 a 100 caracteres.") String nome,

        // @TelefoneBR conta digitos, nao policia mascara — @Size continua
        // porque muitos separadores podem estourar a coluna mesmo com os
        // digitos certos
        @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres.")
        @TelefoneBR
        String telefone,

        @Size(max = 100, message = "O e-mail deve ter no máximo 100 caracteres.")
        @Email(message = "Informe um e-mail em formato válido.")
        String email,

        @Past(message = "A data de nascimento deve estar no passado.")
        @IdadeMaxima(anos = 120, message = "Confira a data de nascimento.")
        LocalDate dataNascimento,

        // "> 0, 2 casas" (spec 6.3). @DecimalMin com inclusive=false e o
        // "maior que zero"; @Digits e as duas casas. Nao precisa de validador
        // proprio — ao contrario de @SenhaForte e @TelefoneBR, o Jakarta
        // expressa esta regra inteira.
        @DecimalMin(value = "0", inclusive = false, message = "O valor da sessão deve ser maior que zero.")
        @Digits(integer = 8, fraction = 2, message = "O valor da sessão deve ter no máximo duas casas decimais.")
        BigDecimal valorSessao,

        CanalAviso canalAviso) {
}
