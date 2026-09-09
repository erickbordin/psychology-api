package com.psychologyapi.psychologyapi.config;

import java.time.LocalDateTime;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;

@Configuration
public class OpenApiConfig {

    /**
     * Todo LocalDateTime do contrato, de entrada OU DE SAIDA, declarado do
     * mesmo jeito (achado da re-revisao C2, laudo-c1-c2-rodada-2.md): o
     * conserto anterior corrigiu so os quatro campos de ENTRADA (dtConsulta
     * x3, dtPrimeiraConsulta) com @Schema por campo, e deixou o springdoc
     * declarar todo campo de SAIDA (ConsultaResponseDTO.dtConsulta e todo
     * LocalDateTime dos outros DTOs de resposta) como {"type":"string",
     * "format":"date-time"} — que na convencao OpenAPI/JSON Schema
     * (https://swagger.io/docs/specification/data-models/data-types/#string)
     * significa RFC 3339 COM offset obrigatorio. A API nunca emite offset
     * (spec 5.9, fuso unico America/Sao_Paulo) — um cliente TypeScript
     * gerado a partir do contrato prometeria uma forma que o servidor nunca
     * manda.
     *
     * Substituicao GLOBAL, nao campo a campo: ha LocalDateTime em
     * ConsultaResponseDTO, nos dois DTOs do painel (SessaoDoDiaDTO,
     * PendenciaDoPainelDTO) e nos DTOs de resposta de paciente, pendencia,
     * usuario e auditoria — anotar alguns e esquecer outros e exatamente
     * como o "problema dos tres dialetos" comeca (o mesmo padrao que a
     * revisao do conjunto ja sinalizou noutro lugar). SpringDocUtils.getConfig()
     * e um singleton estatico do springdoc: o registro precisa acontecer
     * antes do primeiro /v3/api-docs, por isso o bloco estatico — carregado
     * junto com a classe, nao preso a quando o bean @Bean e resolvido.
     */
    static {
        SpringDocUtils.getConfig().replaceWithSchema(LocalDateTime.class, new StringSchema()
                .example("2026-12-02T14:00:00")
                .description("Data e hora local do consultório (fuso America/Sao_Paulo, spec 5.9) — "
                        + "sempre sem offset, no formato yyyy-MM-dd'T'HH:mm:ss."));
    }

    /** Identifica o contrato de onde o front gera os tipos (spec 9). */
    @Bean
    public OpenAPI contrato() {
        return new OpenAPI().info(new Info()
                .title("Psychology API")
                .version("v1")
                .description("API do consultorio: agenda, pacientes, prontuario e financeiro."));
    }
}
