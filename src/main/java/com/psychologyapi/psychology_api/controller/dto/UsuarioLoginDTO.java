package com.psychologyapi.psychology_api.controller.dto;

import org.mapstruct.Mapper;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
@Mapper(componentModel = "spring")
public record UsuarioLoginDTO(
        @Email(message = "invalido") String email_usuario,
        @NotBlank(message = "campo obrigatorio") String senha) {

}
