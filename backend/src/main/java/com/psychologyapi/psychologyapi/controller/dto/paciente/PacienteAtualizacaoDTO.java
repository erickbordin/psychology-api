package com.psychologyapi.psychologyapi.controller.dto.paciente;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

// Sem @NotBlank de proposito: atualizacao e parcial, e a semantica de campo
// ausente apagar o que ja estava salvo e decisao de produto ainda em aberto
// (I6 do laudo da revisao do conjunto, 2026-09-05). Os @Size abaixo so
// espelham o limite que ja existe na coluna do banco, para que estourar o
// limite vire 400 na validacao, nao 500 na constraint.
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
        LocalDate dataNascimento) {
}
