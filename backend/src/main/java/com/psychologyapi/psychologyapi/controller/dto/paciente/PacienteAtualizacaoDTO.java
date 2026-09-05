package com.psychologyapi.psychologyapi.controller.dto.paciente;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

// Sem @NotBlank de proposito: atualizacao e parcial, e a semantica de campo
// ausente apagar o que ja estava salvo e decisao de produto ainda em aberto
// (I6 do laudo da revisao do conjunto, 2026-09-05). Os @Size abaixo so
// espelham o limite que ja existe na coluna do banco, para que estourar o
// limite vire 400 na validacao, nao 500 na constraint.
public record PacienteAtualizacaoDTO(
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.") String nome,
        @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres.") String telefone,
        @Size(max = 100, message = "O e-mail deve ter no máximo 100 caracteres.") String email,
        LocalDate dataNascimento) {
}
