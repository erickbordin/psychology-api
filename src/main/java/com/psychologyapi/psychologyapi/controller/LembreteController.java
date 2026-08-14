package com.psychologyapi.psychologyapi.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.controller.dto.lembrete.LembreteCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.lembrete.LembreteResponseDTO;
import com.psychologyapi.psychologyapi.service.LembreteService;

import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class LembreteController {
    
    private final LembreteService lembreteService;

    @PostMapping("/pacientes/{pacienteId}/lembretes")
    public ResponseEntity<LembreteResponseDTO> cadastrar(@PathVariable UUID pacienteId, @RequestBody @Valid LembreteCadastroDTO dto){
        LembreteResponseDTO lembrete = lembreteService.cadastrar(pacienteId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(lembrete);
    }

    @GetMapping("/pacientes/{pacienteId}/lembretes")
    public ResponseEntity<List<LembreteResponseDTO>> listarPorPaciente(@PathVariable UUID pacienteId){
        List<LembreteResponseDTO> listaDeLembrete = lembreteService.listarPorPaciente(pacienteId);
        return ResponseEntity.status(HttpStatus.OK).body(listaDeLembrete);
    } 


}
