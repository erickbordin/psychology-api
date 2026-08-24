package com.psychologyapi.psychologyapi.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
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
    public ResponseEntity<PagedModel<LembreteResponseDTO>> listarPorPaciente(@PathVariable UUID pacienteId,
            @PageableDefault(size = 20) Pageable pageable){
        Page<LembreteResponseDTO> lembretes = lembreteService.listarPorPaciente(pacienteId, pageable);
        return ResponseEntity.status(HttpStatus.OK).body(new PagedModel<>(lembretes));
    } 


}
