package com.psychologyapi.psychologyapi.controller.dto.consulta;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class HorizonteValidoValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    private static final LocalDateTime PRIMEIRA = LocalDateTime.now().plusDays(7).withHour(15).withMinute(0)
            .withSecond(0).withNano(0);

    @BeforeAll
    static void abrirValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void fecharValidator() {
        factory.close();
    }

    private Set<ConstraintViolation<ConsultaRecorrenteCadastroDTO>> validar(Integer quantidade, LocalDate limite) {
        return validator.validate(
                new ConsultaRecorrenteCadastroDTO(UUID.randomUUID(), PRIMEIRA, quantidade, limite));
    }

    @Test
    @DisplayName("so quantidadeSessoes e valido")
    void apenasQuantidade() {
        assertThat(validar(10, null)).isEmpty();
    }

    @Test
    @DisplayName("so dtLimite e valido")
    void apenasLimite() {
        assertThat(validar(null, PRIMEIRA.plusWeeks(9).toLocalDate())).isEmpty();
    }

    @Test
    @DisplayName("os dois horizontes juntos e invalido, e o erro aponta o campo quantidadeSessoes")
    void osDoisJuntos() {
        Set<ConstraintViolation<ConsultaRecorrenteCadastroDTO>> violacoes =
                validar(10, PRIMEIRA.plusWeeks(9).toLocalDate());

        assertThat(violacoes).hasSize(1);
        assertThat(violacoes.iterator().next().getPropertyPath()).hasToString("quantidadeSessoes");
    }

    @Test
    @DisplayName("nenhum horizonte e invalido")
    void nenhumHorizonte() {
        assertThat(validar(null, null)).hasSize(1);
    }

    @Test
    @DisplayName("quantidade menor que 2 e invalida: serie de uma sessao e consulta avulsa")
    void quantidadeAbaixoDoMinimo() {
        assertThat(validar(1, null)).hasSize(1);
    }

    @Test
    @DisplayName("quantidade acima de 52 e invalida")
    void quantidadeAcimaDoTeto() {
        assertThat(validar(53, null)).hasSize(1);
    }

    @Test
    @DisplayName("dtLimite que estoura o teto e rejeitado, nao truncado")
    void limiteQueEstouraOTeto() {
        assertThat(validar(null, PRIMEIRA.plusYears(5).toLocalDate())).hasSize(1);
    }

    @Test
    @DisplayName("dtLimite que cabe uma ocorrencia so e invalido")
    void limiteQueCabeUmaOcorrencia() {
        assertThat(validar(null, PRIMEIRA.plusDays(3).toLocalDate())).hasSize(1);
    }

    @Test
    @DisplayName("dtLimite anterior a primeira consulta e invalido")
    void limiteAnteriorAPrimeira() {
        assertThat(validar(null, PRIMEIRA.minusDays(1).toLocalDate())).hasSize(1);
    }
}
