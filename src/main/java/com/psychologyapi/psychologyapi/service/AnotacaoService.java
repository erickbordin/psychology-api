package com.psychologyapi.psychologyapi.service;

import com.psychologyapi.psychologyapi.controller.dto.anotacao.AnotacaoCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.anotacao.AnotacaoResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.AnotacaoNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.mapper.AnotacaoMapper;
import com.psychologyapi.psychologyapi.model.Anotacao;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.AnotacaoRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnotacaoService {

        private final AnotacaoRepository anotacaoRepository;
        private final AnotacaoMapper anotacaoMapper;
        private final SecurityService securityService;
        private final PacienteRepository pacienteRepository;
        private final AuditoriaService auditoriaService;

        @Transactional
        public AnotacaoResponseDTO cadastrar(UUID pacienteId, AnotacaoCadastroDTO dto) {

                Usuario usuarioLogado = securityService.obterUsuarioLogado();
                Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, usuarioLogado)
                                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));
                Anotacao anotacao = anotacaoMapper.toEntity(dto);
                anotacao.setPaciente(paciente);
                anotacao.setUsuario(usuarioLogado);
                Anotacao anotacaoSalva = anotacaoRepository.save(anotacao);
                auditoriaService.registrar(usuarioLogado, AcaoAuditoria.CRIACAO, "Anotacao", anotacaoSalva.getId());
                return anotacaoMapper.toResponseDTO(anotacaoSalva);

        }

        public Page<AnotacaoResponseDTO> listarPorPaciente(UUID pacienteId, Pageable pageable) {
                Usuario usuarioLogado = securityService.obterUsuarioLogado();
                Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, usuarioLogado)
                                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));
                Page<Anotacao> anotacoesDoPaciente = anotacaoRepository
                                .findByPacienteAndUsuarioAndDeletedAtIsNullOrderByCreatedAtDesc(paciente,
                                                usuarioLogado, pageable);
                auditoriaService.registrar(usuarioLogado, AcaoAuditoria.VISUALIZACAO, "Paciente", paciente.getId());
                return anotacoesDoPaciente.map(anotacaoMapper::toResponseDTO);
        }

        @Transactional
        public void deletarAnotacao(UUID anotacaoId) {
                Usuario usuarioLogado = securityService.obterUsuarioLogado();
                Anotacao anotacao = anotacaoRepository.findByIdAndUsuarioAndDeletedAtIsNull(anotacaoId, usuarioLogado)
                                .orElseThrow(() -> new AnotacaoNaoEncontradaException("Anotacao nao encontrada"));
                anotacao.setDeletedAt(LocalDateTime.now());
                anotacaoRepository.save(anotacao);
                auditoriaService.registrar(usuarioLogado, AcaoAuditoria.EXCLUSAO, "Anotacao", anotacao.getId());
        }

}
