package com.psychologyapi.psychologyapi.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.psychologyapi.psychologyapi.controller.dto.PacienteCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.PacienteResponseDTO;
import com.psychologyapi.psychologyapi.model.Paciente;

@Mapper(componentModel = "spring")
public interface PacienteMapper {

    @Mapping(target = "nomePaciente", source = "nome")
    @Mapping(target = "emailPaciente", source = "email")
    @Mapping(target = "telefonePaciente", source = "telefone")
    @Mapping(target = "dataNascimentoPaciente", source = "dataNascimento")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usuario", ignore = true)
    @Mapping(target = "observacoesPaciente", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "anotacoes", ignore = true)
    @Mapping(target = "consultas", ignore = true)
    @Mapping(target = "lembretes", ignore = true)
    Paciente toEntity(PacienteCadastroDTO dto);


    @Mapping(target = "idPaciente", source = "id")
    @Mapping(target = "idUsuario", source = "usuario.id")
    @Mapping(target = "nome", source = "nomePaciente")
    @Mapping(target = "email", source = "emailPaciente")
    PacienteResponseDTO toResponseDTO(Paciente paciente);

}
