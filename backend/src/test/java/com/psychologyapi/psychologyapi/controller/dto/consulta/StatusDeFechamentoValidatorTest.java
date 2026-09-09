package com.psychologyapi.psychologyapi.controller.dto.consulta;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import com.psychologyapi.psychologyapi.model.Status;

/**
 * No molde de {@code TelefoneBRValidatorTest}: valida o DTO real por um
 * {@link Validator} real, nao chama {@code isValid} direto no validador —
 * isso prova a anotacao ligada ao campo, nao so o algoritmo.
 */
class StatusDeFechamentoValidatorTest {

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

    private Set<ConstraintViolation<StatusConsultaDTO>> validar(Status status) {
        return validator.validate(new StatusConsultaDTO(status));
    }

    @Test
    @DisplayName("REALIZADA e valido")
    void realizadaEValido() {
        assertThat(validar(Status.REALIZADA)).isEmpty();
    }

    @Test
    @DisplayName("FALTOU e valido")
    void faltouEValido() {
        assertThat(validar(Status.FALTOU)).isEmpty();
    }

    @Test
    @DisplayName("AGENDADA e recusado, com a mensagem que explica o porque")
    void agendadaERecusado() {
        Set<ConstraintViolation<StatusConsultaDTO>> violacoes = validar(Status.AGENDADA);

        assertThat(violacoes).hasSize(1);
        assertThat(violacoes.iterator().next().getPropertyPath()).hasToString("status");
        assertThat(violacoes.iterator().next().getMessage())
                .isEqualTo("Por aqui só é possível marcar a sessão como realizada ou faltou.");
    }

    @Test
    @DisplayName("CANCELADA e recusado, com a mensagem que explica o porque")
    void canceladaERecusado() {
        Set<ConstraintViolation<StatusConsultaDTO>> violacoes = validar(Status.CANCELADA);

        assertThat(violacoes).hasSize(1);
        assertThat(violacoes.iterator().next().getPropertyPath()).hasToString("status");
        assertThat(violacoes.iterator().next().getMessage())
                .isEqualTo("Por aqui só é possível marcar a sessão como realizada ou faltou.");
    }

    @Test
    @DisplayName("nulo dispara so a mensagem do @NotNull: @StatusDeFechamento nao soma uma segunda violacao para o mesmo defeito")
    void nuloDisparaSoUmaViolacao() {
        Set<ConstraintViolation<StatusConsultaDTO>> violacoes = validar(null);

        assertThat(violacoes).hasSize(1);
        assertThat(violacoes.iterator().next().getMessage())
                .isEqualTo("Informe o status da consulta.");
    }

    @Test
    @DisplayName("a anotacao esta ligada ao campo status do DTO, nao so o algoritmo funciona")
    void anotacaoEstaLigadaNoDTO() {
        // AGENDADA e um Status real e nao nulo: a unica violacao possivel
        // aqui e a @StatusDeFechamento. Este teste e o que falha no dia em
        // que alguem tirar a anotacao do DTO. Ela apenas estreita a rota do
        // PATCH aos dois status de fechamento da spec 4① (REALIZADA/FALTOU);
        // quem fecha a brecha de duplo agendamento e a guarda em atualizarStatus.
        Set<ConstraintViolation<StatusConsultaDTO>> violacoes = validar(Status.AGENDADA);

        assertThat(violacoes).hasSize(1);
        assertThat(violacoes.iterator().next().getPropertyPath()).hasToString("status");
    }
}
