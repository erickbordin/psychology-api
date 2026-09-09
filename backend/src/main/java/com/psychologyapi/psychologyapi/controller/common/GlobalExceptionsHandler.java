package com.psychologyapi.psychologyapi.controller.common;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.psychologyapi.psychologyapi.controller.dto.error.ErroCampoDTO;
import com.psychologyapi.psychologyapi.controller.dto.error.ErroRespostaDTO;
import com.psychologyapi.psychologyapi.exceptions.AnotacaoNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.ConflitoDeHorarioException;
import com.psychologyapi.psychologyapi.exceptions.ConsultaNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.PendenciaNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.exceptions.FiltroAuditoriaInvalidoException;
import com.psychologyapi.psychologyapi.exceptions.PeriodoInvalidoException;
import com.psychologyapi.psychologyapi.exceptions.RegistroDuplicadoException;
import com.psychologyapi.psychologyapi.exceptions.SessaoInvalidaException;

/**
 * Todo erro da API sai no mesmo envelope, o {@link ErroRespostaDTO}: status,
 * mensagem e a lista de campos culpados.
 *
 * O 400 preenche a lista com um item por campo. O 401, o 404 e o 409 nao tem
 * campo culpado — a lista vem vazia e a mensagem carrega o motivo. O cliente le
 * uma forma de erro, nao duas: com texto puro em uns status e JSON em outros,
 * tratar falha exigiria dois caminhos de parsing.
 *
 * O 401 da cadeia de seguranca (sem token, token expirado ou invalido) nao passa
 * por aqui — excecao lancada num filtro sobe antes do DispatcherServlet, fora do
 * alcance do @RestControllerAdvice. Quem monta o envelope naquele caso e o
 * ErroRespostaAuthenticationEntryPoint.
 */
@RestControllerAdvice
public class GlobalExceptionsHandler {

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErroRespostaDTO handleBadCredentialsException(BadCredentialsException e) {
        return semCampos(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(SessaoInvalidaException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErroRespostaDTO handleSessaoInvalida(SessaoInvalidaException e) {
        return semCampos(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(RegistroDuplicadoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErroRespostaDTO handleRegistroDuplicadoException(RegistroDuplicadoException e) {
        return semCampos(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroRespostaDTO handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getFieldErrors();
        List<ErroCampoDTO> listaErro = fieldErrors
                .stream()
                .map(fe -> new ErroCampoDTO(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return new ErroRespostaDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de validação",
                listaErro);
    }

    @ExceptionHandler(PacienteNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErroRespostaDTO handlePacienteNaoEncontrado(PacienteNaoEncontradoException e){
        return semCampos(HttpStatus.NOT_FOUND, e.getMessage());
    }   

    @ExceptionHandler(ConsultaNaoEncontradaException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErroRespostaDTO handleConsultaNaoEncontrada(ConsultaNaoEncontradaException e){
        return semCampos(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AnotacaoNaoEncontradaException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErroRespostaDTO handleAnotacaoNaoEncontrada(AnotacaoNaoEncontradaException e){
        return semCampos(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(PendenciaNaoEncontradaException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErroRespostaDTO handlePendenciaNaoEncontrada(PendenciaNaoEncontradaException e){
        return semCampos(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflitoDeHorarioException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErroRespostaDTO handelConflitoDeHorario(ConflitoDeHorarioException e){
        return semCampos(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(PeriodoInvalidoException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroRespostaDTO handlePeriodoInvalido(PeriodoInvalidoException e) {
        return new ErroRespostaDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de validação",
                List.of(new ErroCampoDTO(e.getCampo(), e.getMessage())));
    }

    /**
     * Reaproveita o mesmo formato de 400 da validacao de bean (ErroRespostaDTO):
     * entidadeId sem entidade nao e erro de bind, mas e erro de validacao do
     * mesmo jeito, e o contrato de 400 da API deve ser um so.
     */
    @ExceptionHandler(FiltroAuditoriaInvalidoException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroRespostaDTO handleFiltroAuditoriaInvalido(FiltroAuditoriaInvalidoException e) {
        return new ErroRespostaDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de validação",
                List.of(new ErroCampoDTO("entidadeId", e.getMessage())));
    }

    /**
     * Rede de seguranca contra estouro de limite/constraint que a validacao de
     * bean nao pegou antes (DTO desatualizado em relacao ao banco, ou um campo
     * ainda sem @Size) — sem este handler o cliente leva 500 onde a 6.1 pede
     * 400 (I6 do laudo da revisao do conjunto, 2026-09-05).
     *
     * Nao tenta apontar o campo culpado: a mensagem do Postgres para
     * "value too long" nao carrega o nome da coluna, so o tipo/tamanho, entao
     * qualquer tentativa de adivinhar o campo aqui seria frágil. Apontar o
     * campo com precisao e trabalho do @Size no DTO (validado antes de chegar
     * aqui); este handler so evita que o que escapar disso vire 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroRespostaDTO handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        return semCampos(HttpStatus.BAD_REQUEST,
                "Não foi possível salvar: um dos valores informados é inválido ou excede o limite permitido.");
    }

    /**
     * Corpo que o Jackson nao consegue desserializar — JSON quebrado, ou um
     * valor de enum que nao existe (ex.: {"status":"BANANA"}) — estoura ANTES
     * da validacao de bean, e sem este handler cai no resolver padrao do
     * Spring: 400 com o corpo generico do Spring (timestamp/status/error/path),
     * sem "mensagem" nem "erros". Isso quebra a garantia de envelope unico
     * desta classe, e o front gera os tipos de erro a partir dela.
     *
     * Nao repassa e.getMessage(): a mensagem do Jackson cita nomes de classe,
     * de campo e os valores aceitos do enum — forma interna vazando para
     * qualquer chamador. Mensagem fixa, "erros" vazio como os demais 400 sem
     * campo culpado.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroRespostaDTO handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        return semCampos(HttpStatus.BAD_REQUEST,
                "Não foi possível ler o corpo da requisição. Confira os campos enviados.");
    }

    /**
     * Erro sem campo culpado. A lista vazia — e nao ausente — mantem a forma do
     * envelope estavel: o cliente sempre encontra "erros" como array.
     */
    private static ErroRespostaDTO semCampos(HttpStatus status, String mensagem) {
        return new ErroRespostaDTO(status.value(), mensagem, List.of());
    }

}
