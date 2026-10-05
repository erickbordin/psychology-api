package com.psychologyapi.psychologyapi.controller.dto.usuario;

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

class SenhaForteValidatorTest {

    private final SenhaForteValidator senhaForteValidator = new SenhaForteValidator();

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

    @Test
    @DisplayName("senha da lista de comuns e recusada mesmo com tamanho suficiente")
    void senhaComumERecusada() {
        assertThat(senhaForteValidator.isValid("senha123456", null)).isFalse();
        assertThat(senhaForteValidator.isValid("SENHA123456", null))
                .as("comparacao e insensivel a caixa: trocar para maiuscula nao torna a senha forte")
                .isFalse();
    }

    @Test
    @DisplayName("senha fora da lista passa")
    void senhaIncomumPassa() {
        assertThat(senhaForteValidator.isValid("carroca-azul-de-terca", null)).isTrue();
    }

    @Test
    @DisplayName("nulo e vazio nao sao problema deste validador")
    void nuloEVazioPassam() {
        // @NotBlank e @Size ja cobrem os dois; validador que reclama de nulo
        // faz o usuario levar duas mensagens pelo mesmo defeito
        assertThat(senhaForteValidator.isValid(null, null)).isTrue();
        assertThat(senhaForteValidator.isValid("", null)).isTrue();
    }

    @Test
    @DisplayName("a anotacao esta ligada ao campo senha do DTO, nao so o algoritmo funciona")
    void anotacaoEstaLigadaNoDTO() {
        Set<ConstraintViolation<UsuarioCadastroDTO>> violacoes = validator.validate(
                new UsuarioCadastroDTO("Marina Aguiar", "marina.wiring@example.com", "senha123456", true, "teste-1", "teste-1"));

        // nome e e-mail sao validos, e a senha tem 11 caracteres e nao e branca,
        // entao @NotBlank e @Size passam: a unica violacao possivel e a @SenhaForte.
        // Este teste e o que falha no dia em que alguem tirar a anotacao do DTO —
        // sem ele, remover @SenhaForte deixa a suite verde e o produto furado
        assertThat(violacoes).hasSize(1);
        assertThat(violacoes.iterator().next().getPropertyPath().toString()).isEqualTo("senha");
        assertThat(violacoes.iterator().next().getMessage())
                .isEqualTo("Essa senha é fácil de adivinhar. Escolha outra.");
    }

    @Test
    @DisplayName("senha incomum nao gera violacao nenhuma no DTO")
    void senhaIncomumNaoVioleNada() {
        Set<ConstraintViolation<UsuarioCadastroDTO>> violacoes = validator.validate(
                new UsuarioCadastroDTO("Marina Aguiar", "marina.wiring@example.com", "carroca-azul-de-terca", true, "teste-1", "teste-1"));

        assertThat(violacoes).isEmpty();
    }
}
