package com.psychologyapi.psychologyapi.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.controller.dto.ficha.FichaDoPacienteDTO;
import com.psychologyapi.psychologyapi.service.FichaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/pacientes")
@RequiredArgsConstructor
public class FichaController {

    private final FichaService fichaService;

    @GetMapping("/{pacienteId}/ficha")
    public ResponseEntity<FichaDoPacienteDTO> ficha(@PathVariable UUID pacienteId) {
        return ResponseEntity.status(HttpStatus.OK).body(fichaService.doPaciente(pacienteId));
    }
}
