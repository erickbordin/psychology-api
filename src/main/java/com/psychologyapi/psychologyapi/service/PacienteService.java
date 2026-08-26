package com.psychologyapi.psychologyapi.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.psychologyapi.psychologyapi.controller.dto.paciente.PacienteAtualizacaoDTO;
import com.psychologyapi.psychologyapi.controller.dto.paciente.PacienteCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.paciente.PacienteResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.mapper.PacienteMapper;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PacienteService {

    private final PacienteRepository pacienteRepository;
    private final PacienteMapper pacienteMapper;
    private final SecurityService securityService;
    private final AuditoriaService auditoriaService;

    public PacienteResponseDTO cadastrar(PacienteCadastroDTO dto) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteMapper.toEntity(dto);
        paciente.setUsuario(usuarioLogado);
        Paciente pacienteSalvo = pacienteRepository.save(paciente);
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.CRIACAO, "Paciente", pacienteSalvo.getId());
        return pacienteMapper.toResponseDTO(pacienteSalvo);
    }

    public PacienteResponseDTO buscarPorId(UUID id) {
        Usuario usuario = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, usuario)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));
        auditoriaService.registrar(usuario, AcaoAuditoria.VISUALIZACAO, "Paciente", paciente.getId());
        return pacienteMapper.toResponseDTO(paciente);
    }

    public List<PacienteResponseDTO> listarPacientes(String nome) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        List<Paciente> pacientes = (nome == null || nome.isBlank())
                ? pacienteRepository.findByUsuarioAndDeletedAtIsNull(usuarioLogado)
                : pacienteRepository.findByUsuarioAndNomePacienteContainingIgnoreCaseAndDeletedAtIsNull(usuarioLogado,
                        nome);
        return pacientes.stream()
                .map(pacienteMapper::toResponseDTO)
                .toList();
    }

    public PacienteResponseDTO atualizarPaciente(UUID id, PacienteAtualizacaoDTO dto) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));

        pacienteMapper.atualizarPacienteAPartirDoDto(dto, paciente);

        Paciente pacienteAtualizado = pacienteRepository.save(paciente);
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.ATUALIZACAO, "Paciente", pacienteAtualizado.getId());
        return pacienteMapper.toResponseDTO(pacienteAtualizado);
    }

    public void deletarPaciente(UUID id) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));
        paciente.setDeletedAt(LocalDateTime.now());
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.EXCLUSAO, "Paciente", paciente.getId());
        pacienteRepository.save(paciente);
    }

}
