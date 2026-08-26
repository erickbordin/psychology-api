package com.psychologyapi.psychologyapi.controller;

import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaAtualizacaoDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaRecorrenteCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.SerieCancelamentoResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.SerieConsultaResponseDTO;
import com.psychologyapi.psychologyapi.service.ConsultaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/consultas")
@RequiredArgsConstructor
public class ConsultaController {

    private final ConsultaService consultaService;

    @PostMapping
    public ResponseEntity<ConsultaResponseDTO> cadastrar(@RequestBody @Valid ConsultaCadastroDTO dto){
        ConsultaResponseDTO consulta = consultaService.cadastrar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(consulta);
    }

    @GetMapping
    public ResponseEntity<List<ConsultaResponseDTO>> listarAgenda(
            @RequestParam(required = false) LocalDate de,
            @RequestParam(required = false) LocalDate ate){
        List<ConsultaResponseDTO> consultas = consultaService.listarAgenda(de, ate);
        return ResponseEntity.status(HttpStatus.OK).body(consultas);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConsultaResponseDTO> buscarPorId(@PathVariable UUID id){
        ConsultaResponseDTO consulta = consultaService.buscarPorId(id);
        return ResponseEntity.status(HttpStatus.OK).body(consulta);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConsultaResponseDTO> atualizarConsulta(@PathVariable UUID id, @RequestBody ConsultaAtualizacaoDTO dto){
        ConsultaResponseDTO consultaAtualizada = consultaService.atualizarConsulta(id, dto);
        return ResponseEntity.status(HttpStatus.OK).body(consultaAtualizada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ConsultaResponseDTO> deletarConsulta(@PathVariable UUID id){
        consultaService.deletarConsulta(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/recorrentes")
    public ResponseEntity<SerieConsultaResponseDTO> cadastrarSerie(
            @RequestBody @Valid ConsultaRecorrenteCadastroDTO dto) {
        SerieConsultaResponseDTO serie = consultaService.cadastrarSerie(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(serie);
    }

    @DeleteMapping("/series/{serieId}")
    public ResponseEntity<SerieCancelamentoResponseDTO> cancelarSerie(@PathVariable UUID serieId) {
        SerieCancelamentoResponseDTO cancelamento = consultaService.cancelarSerie(serieId);
        return ResponseEntity.status(HttpStatus.OK).body(cancelamento);
    }

}