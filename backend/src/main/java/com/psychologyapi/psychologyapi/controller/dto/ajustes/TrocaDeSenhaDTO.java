package com.psychologyapi.psychologyapi.controller.dto.ajustes;

import com.psychologyapi.psychologyapi.controller.dto.usuario.CabeNoBcrypt;
import com.psychologyapi.psychologyapi.controller.dto.usuario.SenhaForte;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrocaDeSenhaDTO(
        @NotBlank(message = "Informe a senha atual.") String senhaAtual,
        @NotBlank(message = "Informe uma senha.")
        @Size(min = 10, message = "A senha precisa de pelo menos 10 caracteres.")
        @Size(max = 64, message = "A senha deve ter no máximo 64 caracteres.")
        @CabeNoBcrypt
        @SenhaForte
        String novaSenha) {

}
