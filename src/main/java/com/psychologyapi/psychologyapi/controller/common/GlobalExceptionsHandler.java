package com.psychologyapi.psychologyapi.controller.common;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.psychologyapi.psychologyapi.controller.dto.error.ErroCampoDTO;
import com.psychologyapi.psychologyapi.controller.dto.error.ErroRespostaDTO;
import com.psychologyapi.psychologyapi.exceptions.RegistroDuplicadoException;

@RestControllerAdvice
public class GlobalExceptionsHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<String> handleBadCredentialsException(BadCredentialsException e) {
        return new ResponseEntity<>(e.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(RegistroDuplicadoException.class)
    public ResponseEntity<String> handleRegistroDuplicadoException(RegistroDuplicadoException e) {
        return new ResponseEntity<>(e.getMessage(), HttpStatus.CONFLICT);
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

}
