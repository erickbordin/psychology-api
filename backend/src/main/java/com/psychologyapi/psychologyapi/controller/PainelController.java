package com.psychologyapi.psychologyapi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.controller.dto.painel.PainelHojeDTO;
import com.psychologyapi.psychologyapi.controller.dto.painel.PainelMesDTO;
import com.psychologyapi.psychologyapi.service.PainelService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/painel")
@RequiredArgsConstructor
public class PainelController {

    private final PainelService painelService;

    /** A tela (1) inteira numa chamada (spec 5.6). */
    @GetMapping("/hoje")
    public ResponseEntity<PainelHojeDTO> hoje() {
        return ResponseEntity.ok(painelService.hoje());
    }

    @GetMapping("/mes")
    public ResponseEntity<PainelMesDTO> mes(
            @RequestParam(required = false) Integer ano,
            @RequestParam(required = false) Integer mes) {
        return ResponseEntity.ok(painelService.mes(ano, mes));
    }
}
