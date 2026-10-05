package com.psychologyapi.psychologyapi.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.controller.dto.error.ErroRespostaDTO;
import com.psychologyapi.psychologyapi.controller.dto.termos.TermosAtuaisDTO;
import com.psychologyapi.psychologyapi.service.TermosService;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/termos")
@RequiredArgsConstructor
public class TermosController {

    private final TermosService termosService;

    @GetMapping("/atual")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = TermosAtuaisDTO.class)))
    public ResponseEntity<?> atual() {
        return termosService.atual()
                .<ResponseEntity<?>>map(t -> ResponseEntity.ok(new TermosAtuaisDTO(t.versao(), t.termos(), t.privacidade())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErroRespostaDTO(HttpStatus.NOT_FOUND.value(), "Termos ainda não publicados.", List.of())));
    }

}
