package com.psychologyapi.psychologyapi.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.controller.dto.lembrete.LembreteCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.lembrete.LembreteResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.LembreteNaoEncontradoException;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.mapper.LembreteMapper;
import com.psychologyapi.psychologyapi.model.Lembrete;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.LembreteRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LembreteService {

    private final LembreteRepository lembreteRepository;
    private final LembreteMapper lembreteMapper;
    private final SecurityService securityService;
    private final PacienteRepository pacienteRepository;
    private final AuditoriaService auditoriaService;

    @Transactional
    public LembreteResponseDTO cadastrar(UUID pacienteId, LembreteCadastroDTO dto) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Lembrete lembrete = lembreteMapper.toEntity(dto);
        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));
        lembrete.setPaciente(paciente);
        Lembrete lembreteSalvo = lembreteRepository.save(lembrete);
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.CRIACAO, "Lembrete", lembreteSalvo.getId());
        return lembreteMapper.toResponseDTO(lembreteSalvo);
    }

    public Page<LembreteResponseDTO> listarPorPaciente(UUID pacienteId, Pageable pageable) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));
        Page<Lembrete> lembretes = lembreteRepository
                .findByPacienteAndPaciente_UsuarioAndDeletedAtIsNullOrderByCreatedAtDesc(paciente, usuarioLogado,
                        pageable);
        return lembretes.map(lembreteMapper::toResponseDTO);
    }

    @Transactional
    public void deletarLembrete(UUID lembreteId) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Lembrete lembrete = lembreteRepository.findByIdAndPaciente_UsuarioAndDeletedAtIsNull(lembreteId, usuarioLogado)
                .orElseThrow(() -> new LembreteNaoEncontradoException("Lembrete nao encontrado"));
        lembrete.setDeletedAt(LocalDateTime.now());
        lembreteRepository.save(lembrete);
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.EXCLUSAO, "Lembrete", lembrete.getId());
    }

    @Transactional
    public LembreteResponseDTO concluir(UUID lembreteId) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Lembrete lembrete = lembreteRepository.findByIdAndPaciente_UsuarioAndDeletedAtIsNull(lembreteId, usuarioLogado)
                .orElseThrow(() -> new LembreteNaoEncontradoException("Lembrete nao encontrado"));
        lembrete.setConcluido(true);
        Lembrete lembreteAtualizado = lembreteRepository.save(lembrete);
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.ATUALIZACAO, "Lembrete", lembreteAtualizado.getId());
        return lembreteMapper.toResponseDTO(lembreteAtualizado);
    }

}
