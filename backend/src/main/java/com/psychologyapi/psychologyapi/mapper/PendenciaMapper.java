package com.psychologyapi.psychologyapi.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.psychologyapi.psychologyapi.controller.dto.pendencia.PendenciaCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.pendencia.PendenciaResponseDTO;
import com.psychologyapi.psychologyapi.model.Pendencia;

@Mapper(componentModel = "spring")
public interface PendenciaMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paciente", ignore = true)
    @Mapping(target = "concluido", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Pendencia toEntity(PendenciaCadastroDTO dto);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "descricao", source = "descricao")
    @Mapping(target = "concluido", source = "concluido")
    @Mapping(target = "createdAt", source = "createdAt")
    PendenciaResponseDTO toResponseDTO(Pendencia pendencia);

}
