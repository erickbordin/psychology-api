package com.psychologyapi.psychologyapi.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.controller.dto.pendencia.PendenciaResponseDTO;
import com.psychologyapi.psychologyapi.service.PendenciaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/pendencias")
@RequiredArgsConstructor
public class PendenciaAcoesController {

    private final PendenciaService pendenciaService;

    @DeleteMapping("/{pendenciaId}")
    public ResponseEntity<Void> deletarPendencia(@PathVariable UUID pendenciaId){
        pendenciaService.deletarPendencia(pendenciaId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PatchMapping("/{pendenciaId}/concluir")
    public ResponseEntity<PendenciaResponseDTO> concluir(@PathVariable UUID pendenciaId){
        PendenciaResponseDTO pendencia = pendenciaService.concluir(pendenciaId);
        return ResponseEntity.status(HttpStatus.OK).body(pendencia);
    }

}
