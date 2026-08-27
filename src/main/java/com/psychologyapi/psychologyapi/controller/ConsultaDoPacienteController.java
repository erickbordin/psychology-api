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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaResponseDTO;
import com.psychologyapi.psychologyapi.service.ConsultaService;

import lombok.RequiredArgsConstructor;

/**
 * Sub-recurso sob paciente, como anotacao e lembrete: a URL e a fonte da verdade
 * do pacienteId. Fica fora do ConsultaController porque a raiz e /pacientes.
 */
@RestController
@RequestMapping("/pacientes")
@RequiredArgsConstructor
public class ConsultaDoPacienteController {

    private final ConsultaService consultaService;

    @GetMapping("/{pacienteId}/consultas")
    public ResponseEntity<PagedModel<ConsultaResponseDTO>> listarPorPaciente(@PathVariable UUID pacienteId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ConsultaResponseDTO> consultas = consultaService.listarPorPaciente(pacienteId, pageable);
        return ResponseEntity.status(HttpStatus.OK).body(new PagedModel<>(consultas));
    }

}
