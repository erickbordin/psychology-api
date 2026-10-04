package com.psychologyapi.psychologyapi.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.psychologyapi.psychologyapi.controller.dto.auditoria.LogAuditoriaResponseDTO;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.entity.LogAuditoria;

@Mapper(componentModel = "spring")
public interface LogAuditoriaMapper {

    @Mapping(target = "id", source = "log.id")
    @Mapping(target = "acao", source = "log.acao")
    @Mapping(target = "entidade", source = "log.entidade")
    @Mapping(target = "entidadeId", source = "log.entidadeId")
    @Mapping(target = "createdAt", source = "log.createdAt")
    @Mapping(target = "pacienteId", source = "paciente.id")
    @Mapping(target = "pacienteNome", source = "paciente.nomePaciente")
    @Mapping(target = "pacienteExcluido", expression = "java(paciente == null ? null : paciente.getDeletedAt() != null)")
    LogAuditoriaResponseDTO toResponseDTO(LogAuditoria log, Paciente paciente);

}
