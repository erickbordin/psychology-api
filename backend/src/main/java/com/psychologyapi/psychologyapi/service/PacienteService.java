package com.psychologyapi.psychologyapi.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.controller.dto.paciente.PacienteAtualizacaoDTO;
import com.psychologyapi.psychologyapi.controller.dto.paciente.PacienteCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.paciente.PacienteResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.mapper.PacienteMapper;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PacienteService {

    private final PacienteRepository pacienteRepository;
    private final ConsultaRepository consultaRepository;
    private final PacienteMapper pacienteMapper;
    private final SecurityService securityService;
    private final AuditoriaService auditoriaService;

    @Transactional
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

    @Transactional
    public PacienteResponseDTO atualizarPaciente(UUID id, PacienteAtualizacaoDTO dto) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));

        pacienteMapper.atualizarPacienteAPartirDoDto(dto, paciente);

        Paciente pacienteAtualizado = pacienteRepository.save(paciente);
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.ATUALIZACAO, "Paciente", pacienteAtualizado.getId());
        return pacienteMapper.toResponseDTO(pacienteAtualizado);
    }

    /**
     * Exclusao logica do paciente. Cancela junto as sessoes ainda nao
     * realizadas (spec 5.10, decisao do dono 2026-09-05): sem isso elas
     * continuam aparecendo em Hoje/Agenda com o nome do paciente ja excluido.
     *
     * O historico fica intacto de proposito: consulta com data no passado, ou
     * ja REALIZADA/FALTOU, nao e tocada — prontuario tem prazo legal de
     * guarda (spec 7.3), e sessao que aconteceu e fato, nao agendamento.
     * "Cancelar" aqui e mudar o status para CANCELADA, nunca excluir a
     * consulta (isso e outro fluxo, cancelarSerie, que soft-deleta).
     */
    @Transactional
    public void deletarPaciente(UUID id) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));

        LocalDateTime agora = LocalDateTime.now();
        List<Consulta> aCancelar = consultaRepository.findByPacienteAndUsuarioAndDeletedAtIsNull(paciente, usuarioLogado)
                .stream()
                .filter(consulta -> !consulta.getDtConsulta().isBefore(agora))
                .filter(consulta -> consulta.getStatus() != Status.REALIZADA
                        && consulta.getStatus() != Status.FALTOU
                        && consulta.getStatus() != Status.CANCELADA)
                .toList();

        aCancelar.forEach(consulta -> consulta.setStatus(Status.CANCELADA));
        consultaRepository.saveAll(aCancelar);
        aCancelar.forEach(consulta -> auditoriaService.registrar(usuarioLogado, AcaoAuditoria.ATUALIZACAO, "Consulta",
                consulta.getId()));

        paciente.setDeletedAt(agora);
        pacienteRepository.save(paciente);
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.EXCLUSAO, "Paciente", paciente.getId());
    }

}
