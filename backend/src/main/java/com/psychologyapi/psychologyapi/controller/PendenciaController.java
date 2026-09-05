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

import com.psychologyapi.psychologyapi.controller.dto.pendencia.PendenciaCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.pendencia.PendenciaResponseDTO;
import com.psychologyapi.psychologyapi.service.PendenciaService;

import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PendenciaController {

    private final PendenciaService pendenciaService;

    @PostMapping("/pacientes/{pacienteId}/pendencias")
    public ResponseEntity<PendenciaResponseDTO> cadastrar(@PathVariable UUID pacienteId, @RequestBody @Valid PendenciaCadastroDTO dto){
        PendenciaResponseDTO pendencia = pendenciaService.cadastrar(pacienteId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(pendencia);
    }

    @GetMapping("/pacientes/{pacienteId}/pendencias")
    public ResponseEntity<PagedModel<PendenciaResponseDTO>> listarPorPaciente(@PathVariable UUID pacienteId,
            @PageableDefault(size = 20) Pageable pageable){
        Page<PendenciaResponseDTO> pendencias = pendenciaService.listarPorPaciente(pacienteId, pageable);
        return ResponseEntity.status(HttpStatus.OK).body(new PagedModel<>(pendencias));
    }


}
