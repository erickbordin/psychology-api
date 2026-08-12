package com.psychologyapi.psychologyapi.controller;

import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaAtualizacaoDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaResponseDTO;
import com.psychologyapi.psychologyapi.service.ConsultaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/consultas")
@RequiredArgsConstructor
public class ConsultaController {

    private final ConsultaService consultaService;

    @PostMapping
    public ResponseEntity<Object> cadastrar(@RequestBody @Valid ConsultaCadastroDTO dto){
        ConsultaResponseDTO consulta = consultaService.cadastrar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(consulta);
    }

    @GetMapping
    public ResponseEntity<Object> listarAgendaDoDia(){
        List<ConsultaResponseDTO> consultas = consultaService.listarAgendaDoDia();
        return ResponseEntity.status(HttpStatus.OK).body(consultas);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> buscarPorId(@PathVariable UUID id){
        ConsultaResponseDTO consulta = consultaService.buscarPorId(id);
        return ResponseEntity.status(HttpStatus.OK).body(consulta);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> atualizarConsulta(@PathVariable UUID id, @RequestBody ConsultaAtualizacaoDTO dto){
        ConsultaResponseDTO consultaAtualizada = consultaService.atualizarConsulta(id, dto);
        return ResponseEntity.status(HttpStatus.OK).body(consultaAtualizada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarConsulta(@PathVariable UUID id){
        consultaService.deletarConsulta(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }


}