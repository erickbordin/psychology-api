package com.psychologyapi.psychology_api.mapper;

import org.mapstruct.Mapper;

import com.psychologyapi.psychology_api.controller.dto.UsuarioCadastroDTO;
import com.psychologyapi.psychology_api.controller.dto.UsuarioResponseDTO;
import com.psychologyapi.psychology_api.model.Usuario;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    Usuario toEntity(UsuarioCadastroDTO dto);
    UsuarioResponseDTO toResponseDTO(Usuario usuario);
    
}
