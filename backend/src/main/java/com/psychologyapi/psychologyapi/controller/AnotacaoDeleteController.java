package com.psychologyapi.psychologyapi.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.service.AnotacaoService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/anotacoes")
@RequiredArgsConstructor
public class AnotacaoDeleteController {

    private final AnotacaoService anotacaoService;

    @DeleteMapping("/{anotacaoId}")
    public ResponseEntity<Void> deletarAnotacao(@PathVariable UUID anotacaoId) {
        anotacaoService.deletarAnotacao(anotacaoId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
