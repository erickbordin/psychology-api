package com.psychologyapi.psychologyapi.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.controller.dto.anotacao.AnotacaoCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.anotacao.AnotacaoResponseDTO;
import com.psychologyapi.psychologyapi.service.AnotacaoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/pacientes")
@RequiredArgsConstructor
public class AnotacaoController {

    private final AnotacaoService anotacaoService;

    @PostMapping("/{pacienteId}/anotacoes")
    public ResponseEntity<AnotacaoResponseDTO> cadastrar(@PathVariable UUID pacienteId,
            @RequestBody @Valid AnotacaoCadastroDTO dto) {
        AnotacaoResponseDTO anotacao = anotacaoService.cadastrar(pacienteId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(anotacao);
    }

    @GetMapping("/{pacienteId}/anotacoes")
    public ResponseEntity<List<AnotacaoResponseDTO>> listarPorPaciente(@PathVariable UUID pacienteId) {
        List<AnotacaoResponseDTO> anotacao = anotacaoService.listarPorPaciente(pacienteId);
        return ResponseEntity.status(HttpStatus.OK).body(anotacao);
    }

}
