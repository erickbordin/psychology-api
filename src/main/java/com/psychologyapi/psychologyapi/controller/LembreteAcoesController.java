package com.psychologyapi.psychologyapi.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.controller.dto.lembrete.LembreteResponseDTO;
import com.psychologyapi.psychologyapi.service.LembreteService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/lembretes")
@RequiredArgsConstructor
public class LembreteAcoesController {
    
    private final LembreteService lembreteService;

    @DeleteMapping("/{lembreteId}")
    public ResponseEntity<Void> deletarLembrete(@PathVariable UUID lembreteId){
        lembreteService.deletarLembrete(lembreteId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PatchMapping("/{lembreteId}/concluir")
    public ResponseEntity<LembreteResponseDTO> concluir(@PathVariable UUID lembreteId){
        LembreteResponseDTO lembrete = lembreteService.concluir(lembreteId);
        return ResponseEntity.status(HttpStatus.OK).body(lembrete);
    }

}
