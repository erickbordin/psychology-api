package com.psychologyapi.psychologyapi.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.psychologyapi.psychologyapi.controller.dto.paciente.PacienteAtualizacaoDTO;
import com.psychologyapi.psychologyapi.controller.dto.paciente.PacienteCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.paciente.PacienteResponseDTO;
import com.psychologyapi.psychologyapi.model.Paciente;

@Mapper(componentModel = "spring")
public interface PacienteMapper {

    @Mapping(target = "nomePaciente", source = "nome")
    @Mapping(target = "emailPaciente", source = "email")
    @Mapping(target = "telefonePaciente", source = "telefone")
    @Mapping(target = "dataNascimentoPaciente", source = "dataNascimento")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usuario", ignore = true)
    @Mapping(target = "consentimentoAvisoEm", ignore = true)
    @Mapping(target = "tokenDescadastro", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "anotacoes", ignore = true)
    @Mapping(target = "consultas", ignore = true)
    @Mapping(target = "pendencias", ignore = true)
    Paciente toEntity(PacienteCadastroDTO dto);


    /**
     * Os quatro @Mapping de nome sao obrigatorios, nao esteticos: a entidade
     * sufixa os campos com "Paciente" e o MapStruct nao casa nomes diferentes.
     * telefone e dataNascimento ficaram de fora ate 2026-08-26 e a API devolvia
     * os dois sempre nulos, embora gravados corretamente.
     */
    @Mapping(target = "idPaciente", source = "id")
    @Mapping(target = "idUsuario", source = "usuario.id")
    @Mapping(target = "nome", source = "nomePaciente")
    @Mapping(target = "email", source = "emailPaciente")
    @Mapping(target = "telefone", source = "telefonePaciente")
    @Mapping(target = "dataNascimento", source = "dataNascimentoPaciente")
    PacienteResponseDTO toResponseDTO(Paciente paciente);

    /**
     * NullValuePropertyMappingStrategy.IGNORE: campo ausente no PUT nao mexe no
     * que esta salvo. Sem isto — comportamento ate 14/09/2026, a divida I6 — a
     * estrategia padrao SET_TO_NULL fazia um PUT que so mudava o telefone
     * apagar nome, e-mail, nascimento e valor da sessao. Com `valorSessao` no
     * corpo, aquilo deixava de ser contrato torto e virava perda de dado.
     *
     * Em troca, mandar null nao limpa mais campo nenhum: limpar passa a exigir
     * mecanismo proprio, que nenhuma tela desta fatia pede.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "nomePaciente", source = "dto.nome")
    @Mapping(target = "telefonePaciente", source = "dto.telefone")
    @Mapping(target = "emailPaciente", source = "dto.email")
    @Mapping(target = "dataNascimentoPaciente", source = "dto.dataNascimento")
    void atualizarPacienteAPartirDoDto(PacienteAtualizacaoDTO dto, @MappingTarget Paciente paciente);

}
