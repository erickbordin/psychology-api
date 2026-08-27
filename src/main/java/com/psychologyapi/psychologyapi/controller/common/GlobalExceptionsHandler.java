package com.psychologyapi.psychologyapi.controller.common;

import java.util.List;

import org.springframework.http.HttpStatus;
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
import com.psychologyapi.psychologyapi.exceptions.LembreteNaoEncontradoException;
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

    @ExceptionHandler(LembreteNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErroRespostaDTO handleLembreteNaoEncontrado(LembreteNaoEncontradoException e){
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
     * Erro sem campo culpado. A lista vazia — e nao ausente — mantem a forma do
     * envelope estavel: o cliente sempre encontra "erros" como array.
     */
    private static ErroRespostaDTO semCampos(HttpStatus status, String mensagem) {
        return new ErroRespostaDTO(status.value(), mensagem, List.of());
    }

}
