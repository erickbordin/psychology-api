package com.psychologyapi.psychologyapi.mapper;

import com.psychologyapi.psychologyapi.controller.dto.anotacao.AnotacaoCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.anotacao.AnotacaoResponseDTO;
import com.psychologyapi.psychologyapi.model.Anotacao;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AnotacaoMapper {

    /**
     * O campo do DTO chama-se `anotacao` e o da entidade `conteudo`. Sem este
     * @Mapping explicito o MapStruct nao casa os dois nomes e devolve a entidade
     * vazia — o insert estoura na constraint NOT NULL da coluna e o cliente
     * recebe 500. Ja aconteceu em producao.
     */
    @Mapping(target = "conteudo", source = "anotacao")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usuario", ignore = true)
    @Mapping(target = "paciente", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Anotacao toEntity(AnotacaoCadastroDTO dto);

    @Mapping(target = "pacienteId", source = "paciente.id")
    AnotacaoResponseDTO toResponseDTO(Anotacao anotacao);
}
