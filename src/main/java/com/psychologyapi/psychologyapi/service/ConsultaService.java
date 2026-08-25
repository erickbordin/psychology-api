package com.psychologyapi.psychologyapi.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaAtualizacaoDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaRecorrenteCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.SerieConsultaResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.ConflitoDeHorarioException;
import com.psychologyapi.psychologyapi.exceptions.ConsultaNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.mapper.ConsultaMapper;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;
import com.psychologyapi.psychologyapi.service.serie.OcorrenciasSemanais;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConsultaService {

    private static final long DURACAO_CONSULTA_MINUTOS = 50;

    private final ConsultaMapper consultaMapper;
    private final ConsultaRepository consultaRepository;
    private final PacienteRepository pacienteRepository;
    private final SecurityService securityService;
    private final AuditoriaService auditoriaService;

    public ConsultaResponseDTO cadastrar(ConsultaCadastroDTO dto) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();

        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(dto.pacienteId(), usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));

        Consulta consulta = consultaMapper.toEntity(dto);
        consulta.setUsuario(usuarioLogado);
        consulta.setPaciente(paciente);

        if (dto.dtConsulta() != null) {
            LocalDateTime inicioJanela = dto.dtConsulta().minusMinutes(DURACAO_CONSULTA_MINUTOS);
            LocalDateTime fimJanela = dto.dtConsulta().plusMinutes(DURACAO_CONSULTA_MINUTOS);

            List<Consulta> possiveisConflitos = consultaRepository
                    .findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(usuarioLogado, inicioJanela, fimJanela);

            boolean existeConflito = possiveisConflitos.stream()
                    .anyMatch(c -> c.getStatus() != Status.CANCELADA);

            if (existeConflito) {
                throw new ConflitoDeHorarioException("Ja tem agendamento para este horario");
            }
        }

        Consulta consultaSalva = consultaRepository.save(consulta);
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.CRIACAO, "Consulta", consultaSalva.getId());
        return consultaMapper.toResponseDTO(consultaSalva);
    }

    public ConsultaResponseDTO buscarPorId(UUID id) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Consulta consulta = consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, usuarioLogado)
                .orElseThrow(() -> new ConsultaNaoEncontradaException("Consulta nao encontrada"));
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.VISUALIZACAO, "Consulta", consulta.getId());
        return consultaMapper.toResponseDTO(consulta);
    }

    public List<ConsultaResponseDTO> listarAgendaDoDia() {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();

        LocalDateTime inicioDoDia = LocalDate.now().atStartOfDay();
        LocalDateTime fimDoDia = LocalDate.now().atTime(LocalTime.MAX);

        List<Consulta> consultas = consultaRepository
                .findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(usuarioLogado, inicioDoDia, fimDoDia);

        return consultas.stream()
                .map(consultaMapper::toResponseDTO)
                .toList();
    }

    public ConsultaResponseDTO atualizarConsulta(UUID id, ConsultaAtualizacaoDTO dto) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();

        Consulta consulta = consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, usuarioLogado)
                .orElseThrow(() -> new ConsultaNaoEncontradaException("Consulta nao encontrada"));

        consultaMapper.atualizarConsultaApartirDoDTO(dto, consulta);

        if (dto.dtConsulta() != null) {
            LocalDateTime inicioJanela = dto.dtConsulta().minusMinutes(DURACAO_CONSULTA_MINUTOS);
            LocalDateTime fimJanela = dto.dtConsulta().plusMinutes(DURACAO_CONSULTA_MINUTOS);

            List<Consulta> possiveisConflitos = consultaRepository
                    .findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(usuarioLogado, inicioJanela, fimJanela);

            boolean existeConflito = possiveisConflitos.stream()
                    .anyMatch(c -> c.getStatus() != Status.CANCELADA
                            && !c.getId().equals(consulta.getId()));

            if (existeConflito) {
                throw new ConflitoDeHorarioException("Ja tem agendamento para este horario");
            }
        }

        Consulta consultaAtualizada = consultaRepository.save(consulta);
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.ATUALIZACAO, "Consulta", consultaAtualizada.getId());
        return consultaMapper.toResponseDTO(consultaAtualizada);
    }

    public void deletarConsulta(UUID id) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Consulta consulta = consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, usuarioLogado)
                .orElseThrow(() -> new ConsultaNaoEncontradaException("Consulta nao encontrada"));
        consulta.setDeletedAt(LocalDateTime.now());
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.EXCLUSAO, "Consulta", consulta.getId());
        consultaRepository.save(consulta);
    }

    @Transactional
    public SerieConsultaResponseDTO cadastrarSerie(ConsultaRecorrenteCadastroDTO dto) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();

        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(dto.pacienteId(), usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));

        List<LocalDateTime> ocorrencias = dto.quantidadeSessoes() != null
                ? OcorrenciasSemanais.aPartirDe(dto.dtPrimeiraConsulta(), dto.quantidadeSessoes())
                : OcorrenciasSemanais.ate(dto.dtPrimeiraConsulta(), dto.dtLimite(),
                        OcorrenciasSemanais.MAX_OCORRENCIAS);

        validarConflitoDaSerie(usuarioLogado, ocorrencias);

        UUID serieId = UUID.randomUUID();
        List<Consulta> novas = ocorrencias.stream()
                .map(dtConsulta -> {
                    Consulta consulta = new Consulta();
                    consulta.setUsuario(usuarioLogado);
                    consulta.setPaciente(paciente);
                    consulta.setDtConsulta(dtConsulta);
                    consulta.setSerieId(serieId);
                    return consulta;
                })
                .toList();

        List<Consulta> salvas = consultaRepository.saveAll(novas);
        salvas.forEach(consulta -> auditoriaService.registrar(usuarioLogado, AcaoAuditoria.CRIACAO, "Consulta",
                consulta.getId()));

        return new SerieConsultaResponseDTO(serieId, salvas.size(),
                salvas.stream().map(consultaMapper::toResponseDTO).toList());
    }

    /**
     * Uma query no intervalo inteiro da serie, e a janela de 50 min avaliada em
     * memoria: uma query por ocorrencia seriam ate 52 round trips.
     */
    private void validarConflitoDaSerie(Usuario usuario, List<LocalDateTime> ocorrencias) {
        LocalDateTime inicioJanela = ocorrencias.get(0).minusMinutes(DURACAO_CONSULTA_MINUTOS);
        LocalDateTime fimJanela = ocorrencias.get(ocorrencias.size() - 1).plusMinutes(DURACAO_CONSULTA_MINUTOS);

        List<Consulta> existentes = consultaRepository
                .findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(usuario, inicioJanela, fimJanela)
                .stream()
                .filter(consulta -> consulta.getStatus() != Status.CANCELADA)
                .toList();

        String conflitantes = ocorrencias.stream()
                .filter(ocorrencia -> existentes.stream()
                        .anyMatch(existente -> dentroDaJanela(existente.getDtConsulta(), ocorrencia)))
                .map(LocalDateTime::toString)
                .collect(Collectors.joining(", "));

        if (!conflitantes.isEmpty()) {
            throw new ConflitoDeHorarioException("Conflito de horario em: " + conflitantes);
        }
    }

    private boolean dentroDaJanela(LocalDateTime existente, LocalDateTime nova) {
        return !existente.isBefore(nova.minusMinutes(DURACAO_CONSULTA_MINUTOS))
                && !existente.isAfter(nova.plusMinutes(DURACAO_CONSULTA_MINUTOS));
    }

}
