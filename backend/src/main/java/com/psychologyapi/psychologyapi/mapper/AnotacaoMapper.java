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
    @Mapping(target = "consulta", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Anotacao toEntity(AnotacaoCadastroDTO dto);

    /**
     * `consulta.id` e projecao de propriedade aninhada: o MapStruct gera o
     * null-check sozinho, entao anotacao avulsa sai com consultaId nulo em vez
     * de estourar NPE. O `ignore` na entrada e obrigatorio — sem ele o
     * MapStruct tentaria casar `consultaId` (UUID) com `consulta` (entidade) e
     * o build quebra em "Can't map property".
     */
    @Mapping(target = "pacienteId", source = "paciente.id")
    @Mapping(target = "consultaId", source = "consulta.id")
    AnotacaoResponseDTO toResponseDTO(Anotacao anotacao);
}
