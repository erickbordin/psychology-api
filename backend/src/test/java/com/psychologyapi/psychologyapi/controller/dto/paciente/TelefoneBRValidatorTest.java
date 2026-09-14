package com.psychologyapi.psychologyapi.controller.dto.paciente;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * No molde de {@code IdadeMaximaValidatorTest}: valida o DTO real por um
 * {@link Validator} real, nao chama {@code isValid} direto no validador —
 * isso prova a anotacao ligada ao campo, nao so o algoritmo.
 */
class TelefoneBRValidatorTest {

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

    private Set<ConstraintViolation<PacienteCadastroDTO>> validar(String telefone) {
        return validator.validate(new PacienteCadastroDTO("Helena Vaz", telefone, null, null, null, null));
    }

    @ParameterizedTest(name = "\"{0}\" e um telefone brasileiro valido, mascara torta ou nao")
    @DisplayName("formatos que a @Pattern antiga recusava, e que agora tem que passar")
    @ValueSource(strings = {
            "(51) 99811-2345 ",   // espaco colado no fim: acidente de copiar/colar
            "51 3311.2345",       // ponto como separador, formato de cartao de visita
            "(51) 9 9811-2345",   // separador entre o DDD e o nono digito
            "(51) 9.9811-2345",   // idem, com ponto
            "(51)  99811-2345",   // espaco duplo
    })
    void formatosComMascaraTortaSaoValidos(String telefone) {
        assertThat(validar(telefone)).isEmpty();
    }

    @Test
    @DisplayName("parentese desbalanceado passa: os digitos formam um telefone valido, a mascara e ruido")
    void parenteseDesbalanceadoEValido() {
        assertThat(validar("(5199811234")).isEmpty();
    }

    @Test
    @DisplayName("nulo e valido: telefone e opcional")
    void nuloEValido() {
        assertThat(validar(null)).isEmpty();
    }

    @Test
    @DisplayName("vazio e valido: quem nao tem telefone cadastra assim mesmo")
    void vazioEValido() {
        assertThat(validar("")).isEmpty();
    }

    @Test
    @DisplayName("so espacos e valido: @NotBlank nao se aplica a este campo opcional")
    void soEspacosEValido() {
        assertThat(validar("   ")).isEmpty();
    }

    @Test
    @DisplayName("telefone sem nenhum digito e invalido, e o erro aponta o campo telefone")
    void semDigitosEInvalido() {
        Set<ConstraintViolation<PacienteCadastroDTO>> violacoes = validar("abc");

        assertThat(violacoes).hasSize(1);
        assertThat(violacoes.iterator().next().getPropertyPath()).hasToString("telefone");
        assertThat(violacoes.iterator().next().getMessage())
                .isEqualTo("Informe um telefone brasileiro válido, com DDD.");
    }

    @Test
    @DisplayName("9 digitos e invalido: falta pelo menos um digito de DDD ou numero")
    void noveDigitosEInvalido() {
        assertThat(validar("123456789")).hasSize(1);
    }

    @Test
    @DisplayName("21 digitos e invalido — dispara @TelefoneBR e, por ter 21 caracteres, tambem o @Size(max=20)")
    void vinteEUmDigitosEInvalido() {
        // 21 digitos sem separador = 21 caracteres, entao o @Size(max=20) do
        // proprio campo tambem acende: duas violacoes legitimas, nao uma so.
        // O que este teste prova e que o @TelefoneBR fez a sua parte —
        // confirmado pela mensagem, nao pela contagem de violacoes
        Set<ConstraintViolation<PacienteCadastroDTO>> violacoes = validar("123456789012345678901");

        assertThat(violacoes).isNotEmpty();
        assertThat(violacoes)
                .extracting(ConstraintViolation::getMessage)
                .contains("Informe um telefone brasileiro válido, com DDD.");
    }

    @Test
    @DisplayName("numero estrangeiro de 12/13 digitos que nao comeca com 55 e invalido")
    void numeroEstrangeiroSemPrefixoCincoQuinzeEInvalido() {
        // NOTA: o brief do fix round 1 pedia especificamente "+1 415 555 2671",
        // alegando que tem 13 digitos. Contei com script: "+1 415 555 2671" tem
        // 11 digitos (1+415+555+2671 = 1+3+3+4 = 11), nao 13 — e cai no ramo
        // "case 10, 11 -> true" do validador verbatim, ou seja e ACEITO, o
        // oposto do que o brief queria provar. Troquei pelo numero de ficcao
        // reservado pela Ofcom para uso em midia (020 7946 XXXX, Londres, +44),
        // que tem 12 digitos e nao comeca com 55: esse sim cai no ramo
        // "case 12, 13 -> comeca com 55" e prova a rejeicao pretendida.
        Set<ConstraintViolation<PacienteCadastroDTO>> violacoes = validar("+44 20 7946 0958");

        assertThat(violacoes).hasSize(1);
        assertThat(violacoes.iterator().next().getMessage())
                .isEqualTo("Informe um telefone brasileiro válido, com DDD.");
    }

    @Test
    @DisplayName("\"+1 415 555 2671\" tem 11 digitos (nao 13) e por isso E aceito pelo validador verbatim")
    void numeroDosEUACitadoNoBriefTemOnzeDigitosEEAceito() {
        // Registra o comportamento real do numero citado no fix round 1, para
        // nao deixar a divergencia muda: com o validador exatamente como
        // especificado, "+1 415 555 2671" (11 digitos) passa pelo ramo
        // "case 10, 11 -> true" sem checar prefixo de pais. Nao alterei o
        // validador para corrigir isso — e uma decisao de produto (se um
        // numero de 11 digitos sem DDI deve ser tratado como suspeito), nao
        // algo para eu decidir sozinho.
        assertThat(validar("+1 415 555 2671")).isEmpty();
    }

    @Test
    @DisplayName("13 digitos comecando com 55 (DDI) e valido: celular BR de 11 digitos com codigo do pais na frente")
    void treZeDigitosComDDIEValido() {
        assertThat(validar("5551998112345")).isEmpty();
    }

    @Test
    @DisplayName("a anotacao esta ligada ao campo telefone do DTO, nao so o algoritmo funciona")
    void anotacaoEstaLigadaNoDTO() {
        // "abc" nao tem nenhum digito: a unica violacao possivel e a @TelefoneBR.
        // Este teste e o que falha no dia em que alguem tirar a anotacao do DTO —
        // sem ele, remover @TelefoneBR deixa a suite verde e o produto furado
        Set<ConstraintViolation<PacienteCadastroDTO>> violacoes = validar("abc");

        assertThat(violacoes).hasSize(1);
        assertThat(violacoes.iterator().next().getPropertyPath()).hasToString("telefone");
    }
}
