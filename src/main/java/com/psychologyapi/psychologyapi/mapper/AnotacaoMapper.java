package com.psychologyapi.psychologyapi.mapper;

import com.psychologyapi.psychologyapi.controller.dto.AnotacaoCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.AnotacaoResponseDTO;
import com.psychologyapi.psychologyapi.model.Anotacao;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AnotacaoMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usuario", ignore = true)
    @Mapping(target = "paciente", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Anotacao toEntity(AnotacaoCadastroDTO dto);

    @Mapping(target = "pacienteId", source = "paciente.id")
    AnotacaoResponseDTO toResponseDTO(Anotacao anotacao);
}
