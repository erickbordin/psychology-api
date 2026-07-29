package com.psychologyapi.psychology_api.controller.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public record UsuarioResponseDTO(
    UUID id,
    String nomeUsuario,
    String emailUsuario,
    LocalDateTime createdAt

){

}