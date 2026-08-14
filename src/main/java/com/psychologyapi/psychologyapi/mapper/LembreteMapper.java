package com.psychologyapi.psychologyapi.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.psychologyapi.psychologyapi.controller.dto.lembrete.LembreteCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.lembrete.LembreteResponseDTO;
import com.psychologyapi.psychologyapi.model.Lembrete;

@Mapper(componentModel = "spring")
public interface LembreteMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paciente", ignore = true)
    @Mapping(target = "concluido", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Lembrete toEntity(LembreteCadastroDTO dto);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "descricao", source = "descricao")
    @Mapping(target = "concluido", source = "concluido")
    @Mapping(target = "createdAt", source = "createdAt")
    LembreteResponseDTO toResponseDTO(Lembrete lembrete);

}
