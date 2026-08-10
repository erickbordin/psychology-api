package com.psychologyapi.psychologyapi.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.psychologyapi.psychologyapi.controller.dto.ConsultaCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.ConsultaResponseDTO;
import com.psychologyapi.psychologyapi.model.Consulta;

@Mapper(componentModel = "spring")
public interface ConsultaMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usuario", ignore = true)
    @Mapping(target = "paciente", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Consulta toEntity(ConsultaCadastroDTO dto);

    @Mapping(target = "pacienteId", source = "paciente.id")
    @Mapping(target = "pacienteNome", source = "paciente.nomePaciente")
    ConsultaResponseDTO toResponseDTO(Consulta consulta);

}
