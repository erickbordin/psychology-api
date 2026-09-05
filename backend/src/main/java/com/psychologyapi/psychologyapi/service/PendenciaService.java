package com.psychologyapi.psychologyapi.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.controller.dto.pendencia.PendenciaCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.pendencia.PendenciaResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.PendenciaNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.mapper.PendenciaMapper;
import com.psychologyapi.psychologyapi.model.Pendencia;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.PendenciaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PendenciaService {

    private final PendenciaRepository pendenciaRepository;
    private final PendenciaMapper pendenciaMapper;
    private final SecurityService securityService;
    private final PacienteRepository pacienteRepository;
    private final AuditoriaService auditoriaService;

    @Transactional
    public PendenciaResponseDTO cadastrar(UUID pacienteId, PendenciaCadastroDTO dto) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Pendencia pendencia = pendenciaMapper.toEntity(dto);
        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));
        pendencia.setPaciente(paciente);
        Pendencia pendenciaSalva = pendenciaRepository.save(pendencia);
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.CRIACAO, "Pendencia", pendenciaSalva.getId());
        return pendenciaMapper.toResponseDTO(pendenciaSalva);
    }

    public Page<PendenciaResponseDTO> listarPorPaciente(UUID pacienteId, Pageable pageable) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));
        Page<Pendencia> pendencias = pendenciaRepository
                .findByPacienteAndPaciente_UsuarioAndDeletedAtIsNullOrderByCreatedAtDesc(paciente, usuarioLogado,
                        pageable);
        return pendencias.map(pendenciaMapper::toResponseDTO);
    }

    @Transactional
    public void deletarPendencia(UUID pendenciaId) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Pendencia pendencia = pendenciaRepository.findByIdAndPaciente_UsuarioAndDeletedAtIsNull(pendenciaId, usuarioLogado)
                .orElseThrow(() -> new PendenciaNaoEncontradaException("Pendencia nao encontrada"));
        pendencia.setDeletedAt(LocalDateTime.now());
        pendenciaRepository.save(pendencia);
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.EXCLUSAO, "Pendencia", pendencia.getId());
    }

    @Transactional
    public PendenciaResponseDTO concluir(UUID pendenciaId) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Pendencia pendencia = pendenciaRepository.findByIdAndPaciente_UsuarioAndDeletedAtIsNull(pendenciaId, usuarioLogado)
                .orElseThrow(() -> new PendenciaNaoEncontradaException("Pendencia nao encontrada"));
        pendencia.setConcluido(true);
        Pendencia pendenciaAtualizada = pendenciaRepository.save(pendencia);
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.ATUALIZACAO, "Pendencia", pendenciaAtualizada.getId());
        return pendenciaMapper.toResponseDTO(pendenciaAtualizada);
    }

}
