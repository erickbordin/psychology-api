package com.psychologyapi.psychologyapi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.controller.dto.financeiro.FinanceiroMesDTO;
import com.psychologyapi.psychologyapi.service.FinanceiroService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/financeiro")
@RequiredArgsConstructor
public class FinanceiroController {

    private final FinanceiroService financeiroService;

    @GetMapping("/mes")
    public ResponseEntity<FinanceiroMesDTO> doMes(
            @RequestParam(required = false) Integer ano,
            @RequestParam(required = false) Integer mes) {
        return ResponseEntity.ok(financeiroService.doMes(ano, mes));
    }
}
