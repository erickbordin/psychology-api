package com.psychologyapi.psychologyapi.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.controller.dto.PacienteAtualizacaoDTO;
import com.psychologyapi.psychologyapi.controller.dto.PacienteCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.PacienteResponseDTO;
import com.psychologyapi.psychologyapi.service.PacienteService;

import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/pacientes")
@RequiredArgsConstructor
public class PacienteController {

    private final PacienteService pacienteService;

    @PostMapping
    public ResponseEntity<Object> cadastrarPaciente(@RequestBody @Valid PacienteCadastroDTO dto){
        PacienteResponseDTO paciente = pacienteService.cadastrar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(paciente);
    }
    
    @GetMapping
    public ResponseEntity<Object> listarPacientes(@RequestParam String nome){

        List<PacienteResponseDTO> listaDePacientes = pacienteService.listarPacientes(nome);
        return ResponseEntity.status(HttpStatus.OK).body(listaDePacientes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> buscarPorId(@PathVariable UUID id){
        PacienteResponseDTO paciente = pacienteService.buscarPorId(id);
        return ResponseEntity.status(HttpStatus.OK).body(paciente);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> atualizarPaciente(@PathVariable UUID id, @RequestBody PacienteAtualizacaoDTO dto){
        PacienteResponseDTO paciente = pacienteService.atualizarPaciente(id, dto);
        return ResponseEntity.status(HttpStatus.OK).body(paciente);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarPaciente(@PathVariable UUID id){
        pacienteService.deletarPaciente(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }


}
