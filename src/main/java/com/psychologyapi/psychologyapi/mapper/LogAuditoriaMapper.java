package com.psychologyapi.psychologyapi.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.psychologyapi.psychologyapi.controller.dto.auditoria.LogAuditoriaResponseDTO;
import com.psychologyapi.psychologyapi.model.entity.LogAuditoria;

@Mapper(componentModel = "spring")
public interface LogAuditoriaMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "acao", source = "acao")
    @Mapping(target = "entidade", source = "entidade")
    @Mapping(target = "entidadeId", source = "entidadeId")
    @Mapping(target = "createdAt", source = "createdAt")
    LogAuditoriaResponseDTO toResponseDTO(LogAuditoria log);

}
