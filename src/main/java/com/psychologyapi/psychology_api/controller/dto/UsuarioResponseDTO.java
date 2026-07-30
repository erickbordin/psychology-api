package com.psychologyapi.psychology_api.controller.dto;

import java.time.LocalDateTime;
import java.util.UUID;
public record UsuarioResponseDTO(
    UUID id,
    String nomeUsuario,
    String emailUsuario,
    LocalDateTime createdAt

){

}