package com.psychologyapi.psychologyapi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.controller.dto.ajustes.AjustesAtualizacaoDTO;
import com.psychologyapi.psychologyapi.controller.dto.ajustes.AjustesResponseDTO;
import com.psychologyapi.psychologyapi.service.AjustesService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/ajustes")
@RequiredArgsConstructor
public class AjustesController {

    private final AjustesService ajustesService;

    @GetMapping
    public ResponseEntity<AjustesResponseDTO> obter() {
        return ResponseEntity.ok(ajustesService.obter());
    }

    @PutMapping
    public ResponseEntity<AjustesResponseDTO> atualizar(@RequestBody @Valid AjustesAtualizacaoDTO dto) {
        return ResponseEntity.ok(ajustesService.atualizar(dto));
    }
}
