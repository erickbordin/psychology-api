package com.psychologyapi.psychologyapi.service;

import com.psychologyapi.psychologyapi.controller.dto.anotacao.AnotacaoCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.anotacao.AnotacaoResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.AnotacaoNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.mapper.AnotacaoMapper;
import com.psychologyapi.psychologyapi.model.Anotacao;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.AnotacaoRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnotacaoService {

    private final AnotacaoRepository anotacaoRepository;
    private final AnotacaoMapper anotacaoMapper;
    private final SecurityService securityService;
    private final PacienteRepository pacienteRepository;

    public AnotacaoResponseDTO cadastrar(UUID pacienteId, AnotacaoCadastroDTO dto) {

        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteRepository.findByIdAndUsuario(pacienteId, usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));
        Anotacao anotacao = anotacaoMapper.toEntity(dto);
        anotacao.setPaciente(paciente);
        anotacao.setUsuario(usuarioLogado);
        Anotacao anotacaoSalva = anotacaoRepository.save(anotacao);
        return anotacaoMapper.toResponseDTO(anotacaoSalva);

    }

    public List<AnotacaoResponseDTO> listarPorPaciente(UUID pacienteId) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteRepository.findByIdAndUsuario(pacienteId, usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));
        List<Anotacao> anotacoesDoPaciente = anotacaoRepository.findByPacienteAndUsuarioOrderByCreatedAtDesc(paciente,
                usuarioLogado);
        return anotacoesDoPaciente.stream()
                .map(anotacaoMapper::toResponseDTO).toList();
    }

    public void deletarAnotacao(UUID anotacaoId) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Anotacao anotacao = anotacaoRepository.findByIdAndUsuario(anotacaoId, usuarioLogado)
                .orElseThrow(() -> new AnotacaoNaoEncontradaException("Anotacao nao encontrada"));
        anotacaoRepository.delete(anotacao);
    }

}
