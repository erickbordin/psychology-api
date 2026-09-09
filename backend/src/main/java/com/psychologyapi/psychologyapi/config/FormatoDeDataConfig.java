package com.psychologyapi.psychologyapi.config;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

/**
 * Uma unica forma de LocalDateTime aceita em toda a API — spec 5.9, fuso
 * unico America/Sao_Paulo, sem offset em lugar nenhum. Os campos de corpo
 * (Jackson, via @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")) ja seguem
 * essa regra. Este bean estende a mesma regra para os @RequestParam/@PathVariable
 * de LocalDateTime, porque o caminho padrao do Spring para eles NAO e estrito
 * do jeito que parece.
 *
 * O mecanismo (investigado por leitura do fonte de Spring 6.2.9 e confirmado
 * por experimento, ver fix-c2-rodada-2-report.md): um @RequestParam LocalDateTime
 * anotado com @DateTimeFormat(pattern = "...") e convertido por
 * org.springframework.format.datetime.standard.TemporalAccessorParser, que
 * tenta o padrao estrito primeiro mas, se falhar, cai SILENCIOSAMENTE para
 * LocalDateTime.parse(texto) (ISO_LOCAL_DATE_TIME) quando a anotacao nao
 * declara fallbackPatterns() — que e o caso de todo @DateTimeFormat(pattern=...)
 * sem esse atributo extra. ISO_LOCAL_DATE_TIME aceita segundos e fracao de
 * segundo como OPCIONAIS, entao "14:00" e "14:00:00.123" sao aceitos por esse
 * fallback embora o padrao declarado exija segundos. Um Converter<String,
 * LocalDateTime> registrado como bean nao passa por esse fallback: o Spring
 * Boot registra beans Converter/Formatter/GenericConverter DEPOIS dos
 * formatters padrao (ApplicationConversionService.addBeans, chamado a partir
 * de WebMvcAutoConfigurationAdapter.addFormatters) e o registro mais recente
 * e tentado primeiro (GenericConversionService$ConvertersForPair.add usa
 * addFirst) — como este Converter nao e um ConditionalGenericConverter, ele
 * casa incondicionalmente e vence qualquer conversao String->LocalDateTime
 * de parametro/path variable da aplicacao inteira. Hoje so existe um campo
 * desse tipo (dtConsulta de GET /consultas/conflito), entao o efeito pratico
 * e igual a um conserto local, mas sem deixar a mesma armadilha aberta para
 * o proximo @RequestParam LocalDateTime que alguem declarar.
 */
@Configuration
public class FormatoDeDataConfig {

    /** O mesmo padrao que ConsultaCadastroDTO/ConsultaAtualizacaoDTO/
     *  ConsultaRecorrenteCadastroDTO usam via @JsonFormat — sem resolver
     *  style customizado, para casar exatamente o parsing que o Jackson ja
     *  faz nesses tres campos de corpo (nenhum third dialect). */
    private static final DateTimeFormatter FORMATO_UNICO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Bean
    public Converter<String, LocalDateTime> localDateTimeParametroConverter() {
        return texto -> {
            if (texto == null || texto.isEmpty()) {
                return null;
            }
            // DateTimeParseException sobe sem tratamento aqui de proposito: o
            // Spring embrulha em MethodArgumentTypeMismatchException, e
            // GlobalExceptionsHandler.handleMethodArgumentTypeMismatchException
            // ja responde com mensagem fixa e nunca le a causa/mensagem desta
            // excecao nem o valor bruto — 6.3 fica garantido la, nao aqui.
            return LocalDateTime.parse(texto, FORMATO_UNICO);
        };
    }
}
