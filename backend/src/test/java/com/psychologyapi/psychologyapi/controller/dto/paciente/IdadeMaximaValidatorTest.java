package com.psychologyapi.psychologyapi.controller.dto.paciente;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * No molde de {@code HorizonteValidoValidatorTest}: valida o DTO real por um
 * {@link Validator} real, nao chama {@code isValid} direto no validador. Isso
 * garante que o teste tambem prova a anotacao ligada ao campo — remover
 * {@code @IdadeMaxima} de {@link PacienteCadastroDTO} derruba
 * {@link #idadeAcimaDoTetoEInvalida()}.
 */
class IdadeMaximaValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void abrirValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void fecharValidator() {
        factory.close();
    }

    private Set<ConstraintViolation<PacienteCadastroDTO>> validar(LocalDate nascimento) {
        return validator.validate(new PacienteCadastroDTO("Helena Vaz", null, null, nascimento, null, null));
    }

    @Test
    @DisplayName("nascimento nulo e valido: a data de nascimento e opcional")
    void nascimentoNuloEValido() {
        assertThat(validar(null)).isEmpty();
    }

    @Test
    @DisplayName("exatamente 120 anos e valido — o teto e inclusivo")
    void idadeNoTetoExatoEValida() {
        assertThat(validar(LocalDate.now().minusYears(120))).isEmpty();
    }

    @Test
    @DisplayName("120 anos e 1 dia ainda e valido: o proximo aniversario nao chegou")
    void umDiaAposOAniversarioDe120AindaEValido() {
        assertThat(validar(LocalDate.now().minusYears(120).minusDays(1))).isEmpty();
    }

    @Test
    @DisplayName("121 anos e invalido, e o erro aponta o campo dataNascimento")
    void idadeAcimaDoTetoEInvalida() {
        Set<ConstraintViolation<PacienteCadastroDTO>> violacoes = validar(LocalDate.now().minusYears(121));

        assertThat(violacoes).hasSize(1);
        assertThat(violacoes.iterator().next().getPropertyPath()).hasToString("dataNascimento");
        assertThat(violacoes.iterator().next().getMessage()).isEqualTo("Confira a data de nascimento.");
    }

    @Test
    @DisplayName("data de nascimento no futuro nao e problema deste validador: quem reclama e o @Past")
    void dataNoFuturoNaoViolaIdadeMaxima() {
        Set<ConstraintViolation<PacienteCadastroDTO>> violacoes = validar(LocalDate.now().plusYears(1));

        assertThat(violacoes).hasSize(1);
        assertThat(violacoes.iterator().next().getMessage())
                .isEqualTo("A data de nascimento deve estar no passado.");
    }
}
