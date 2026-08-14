package com.psychologyapi.psychologyapi.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.psychologyapi.psychologyapi.controller.dto.lembrete.LembreteCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.lembrete.LembreteResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.LembreteNaoEncontradoException;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.mapper.LembreteMapper;
import com.psychologyapi.psychologyapi.model.Lembrete;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
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

    public LembreteResponseDTO cadastrar(UUID pacienteId, LembreteCadastroDTO dto) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Lembrete lembrete = lembreteMapper.toEntity(dto);
        Paciente paciente = pacienteRepository.findByIdAndUsuario(pacienteId, usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));
        lembrete.setPaciente(paciente);
        Lembrete lembreteSalvo = lembreteRepository.save(lembrete);
        return lembreteMapper.toResponseDTO(lembreteSalvo);
    }

    public List<LembreteResponseDTO> listarPorPaciente(UUID pacienteId) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteRepository.findByIdAndUsuario(pacienteId, usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));
        List<Lembrete> lembrete = lembreteRepository.findByPacienteAndPaciente_UsuarioOrderByCreatedAtDesc(paciente,
                usuarioLogado);
        return lembrete.stream().map(lembreteMapper::toResponseDTO).toList();
    }

    public void deletarLembrete(UUID lembreteId) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Lembrete lembrete = lembreteRepository.findByIdAndPaciente_Usuario(lembreteId, usuarioLogado)
                .orElseThrow(() -> new LembreteNaoEncontradoException("Lembrete nao encontrado"));
        lembreteRepository.delete(lembrete);
    }

    public LembreteResponseDTO concluir(UUID lembreteId) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Lembrete lembrete = lembreteRepository.findByIdAndPaciente_Usuario(lembreteId, usuarioLogado)
                .orElseThrow(() -> new LembreteNaoEncontradoException("Lembrete nao encontrado"));
        lembrete.setConcluido(true);
        Lembrete lembreteAtualizado = lembreteRepository.save(lembrete);
        return lembreteMapper.toResponseDTO(lembreteAtualizado);
    }

}
