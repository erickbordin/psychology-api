package com.psychologyapi.psychologyapi.controller;

import com.psychologyapi.psychologyapi.controller.dto.consulta.ConflitoResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaAtualizacaoDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaRecorrenteCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.SerieCancelamentoResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.SerieConsultaResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.StatusConsultaDTO;
import com.psychologyapi.psychologyapi.service.ConsultaService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /** Responde se o horario esta livre, SEM criar nada (spec 5.6). E o que
     *  permite avisar do conflito ANTES do envio (6.3) — sem esta rota, o
     *  front so descobre apanhando no POST.
     *
     *  excluirConsultaId e opcional e existe para o dialogo de EDICAO: sem
     *  ele, revalidar o horario da propria consulta a encontraria como seu
     *  proprio conflito. O POST de criacao nao manda o parametro e continua
     *  funcionando exatamente como antes.
     *
     *  SEM @DateTimeFormat de proposito: quem faz o parsing estrito de
     *  dtConsulta e o Converter<String, LocalDateTime> de FormatoDeDataConfig,
     *  registrado como bean e por isso tentado ANTES do parser que o
     *  @DateTimeFormat ligaria (Spring Boot registra beans Converter depois
     *  dos formatters padrao, e o registro mais recente ganha a conversao).
     *  Deixar a anotacao aqui declarando o mesmo padrao seria enganoso — ela
     *  nunca chegaria a rodar. Ver FormatoDeDataConfig para o mecanismo
     *  completo (fix-c2-rodada-2-report.md). */
    @GetMapping("/conflito")
    public ResponseEntity<ConflitoResponseDTO> conflito(
            @RequestParam
            @Schema(type = "string", example = "2026-12-02T14:00:00",
                    description = "Data e hora local do consultório, sem offset — o formato é "
                            + "yyyy-MM-dd'T'HH:mm:ss e um valor com offset (ex.: 'Z' ou '-03:00') "
                            + "é recusado, não convertido.")
            LocalDateTime dtConsulta,
            @RequestParam(required = false) UUID excluirConsultaId) {
        return ResponseEntity.ok(consultaService.checarConflito(dtConsulta, excluirConsultaId));
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

    /** Realizada/Faltou em um clique na propria linha (spec 4①), sem reenviar
     *  dtConsulta — ver StatusConsultaDTO para o motivo de nao reaproveitar o PUT. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ConsultaResponseDTO> atualizarStatus(
            @PathVariable UUID id, @Valid @RequestBody StatusConsultaDTO dto){
        ConsultaResponseDTO consultaAtualizada = consultaService.atualizarStatus(id, dto);
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