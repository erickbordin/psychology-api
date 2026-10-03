package com.psychologyapi.psychologyapi.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.controller.dto.consulta.BaixaEmLoteDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.BaixaEmLoteResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ValorDaConsultaDTO;
import com.psychologyapi.psychologyapi.service.PagamentoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/consultas")
@RequiredArgsConstructor
public class PagamentoController {

    private final PagamentoService pagamentoService;

    @PatchMapping("/{consultaId}/baixa")
    public ResponseEntity<ConsultaResponseDTO> darBaixa(@PathVariable UUID consultaId) {
        return ResponseEntity.ok(pagamentoService.darBaixa(consultaId));
    }

    @DeleteMapping("/{consultaId}/baixa")
    public ResponseEntity<ConsultaResponseDTO> desfazerBaixa(@PathVariable UUID consultaId) {
        return ResponseEntity.ok(pagamentoService.desfazerBaixa(consultaId));
    }

    @PostMapping("/baixas")
    public ResponseEntity<BaixaEmLoteResponseDTO> darBaixaEmLote(@RequestBody @Valid BaixaEmLoteDTO dto) {
        return ResponseEntity.ok(pagamentoService.darBaixaEmLote(dto));
    }

    @PatchMapping("/{consultaId}/valor")
    public ResponseEntity<ConsultaResponseDTO> alterarValor(
            @PathVariable UUID consultaId, @RequestBody @Valid ValorDaConsultaDTO dto) {
        return ResponseEntity.ok(pagamentoService.alterarValor(consultaId, dto));
    }
}
