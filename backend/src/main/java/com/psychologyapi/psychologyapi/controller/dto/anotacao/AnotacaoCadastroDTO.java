package com.psychologyapi.psychologyapi.controller.dto.anotacao;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record AnotacaoCadastroDTO(
        @NotBlank(message = "Informe o conteúdo da anotação.") String anotacao,

        /** A sessao a que esta anotacao pertence (spec 5.1). Opcional: nulo e
         *  anotacao avulsa, que vale mas nao entra na linha do tempo. */
        UUID consultaId
) {}
