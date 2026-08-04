package com.psychologyapi.psychologyapi.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.psychologyapi.psychologyapi.controller.dto.UsuarioCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.UsuarioResponseDTO;
import com.psychologyapi.psychologyapi.model.Usuario;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "anotacoes", ignore = true)
    @Mapping(target = "consultas", ignore = true)
    @Mapping(target = "pacientes", ignore = true)
    @Mapping(target = "nomeUsuario", source = "nome")
    @Mapping(target = "emailUsuario", source = "email")
    @Mapping(target = "senhaUsuario", source = "senha")
    Usuario toEntity(UsuarioCadastroDTO dto);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "nomeUsuario", source = "nomeUsuario")
    @Mapping(target = "emailUsuario", source = "emailUsuario")
    @Mapping(target = "createdAt", source = "createdAt")
    UsuarioResponseDTO toResponseDTO(Usuario usuario);
}