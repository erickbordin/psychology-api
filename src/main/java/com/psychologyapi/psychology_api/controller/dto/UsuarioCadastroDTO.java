package com.psychologyapi.psychology_api.controller.dto;

import org.mapstruct.Mapper;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Mapper(componentModel = "spring")
public record UsuarioCadastroDTO(
        @NotBlank(message = "campo obrigatorio") String nome,
        @Email(message = "invalido") String email,
        @NotBlank(message = "campo obrigatorio") String senha) {

}
