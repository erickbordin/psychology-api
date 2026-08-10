package com.psychologyapi.psychologyapi.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.psychologyapi.psychologyapi.controller.dto.ConsultaAtualizacaoDTO;
import com.psychologyapi.psychologyapi.controller.dto.ConsultaCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.ConsultaResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.ConsultaNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.mapper.ConsultaMapper;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConsultaService {

    private final ConsultaRepository consultaRepository;
    private final ConsultaMapper consultaMapper;

    private final PacienteRepository pacienteRepository;
    private final SecurityService securityService;

    public ConsultaResponseDTO cadastrar(ConsultaCadastroDTO dto) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();

        Paciente paciente = pacienteRepository.findByIdAndUsuario(dto.pacienteId(), usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));

        Consulta consulta = consultaMapper.toEntity(dto);
        consulta.setUsuario(usuarioLogado);
        consulta.setPaciente(paciente);

        Consulta consultaSalva = consultaRepository.save(consulta);
        return consultaMapper.toResponseDTO(consultaSalva);
    }

    public ConsultaResponseDTO buscarPorId(UUID id) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Consulta consulta = consultaRepository.findByIdAndUsuario(id, usuarioLogado)
                .orElseThrow(() -> new ConsultaNaoEncontradaException("Consulta nao encontrada"));
        return consultaMapper.toResponseDTO(consulta);
    }

    public List<ConsultaResponseDTO> listarAgendaDoDia() {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();

        LocalDateTime inicioDoDia = LocalDate.now().atStartOfDay();
        LocalDateTime fimDoDia = LocalDate.now().atTime(LocalTime.MAX);

        List<Consulta> consultas = consultaRepository
                .findByUsuarioAndDtConsultaBetween(usuarioLogado, inicioDoDia, fimDoDia);

        return consultas.stream()
                .map(consultaMapper::toResponseDTO)
                .toList();
    }

    public ConsultaResponseDTO atualizarConsulta(UUID id, ConsultaAtualizacaoDTO dto) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();

        Consulta consulta = consultaRepository.findByIdAndUsuario(id, usuarioLogado)
                .orElseThrow(() -> new ConsultaNaoEncontradaException("Consulta nao encontrada"));

        consultaMapper.atualizarConsultaApartirDoDTO(dto, consulta);
        Consulta consultaAtualizada = consultaRepository.save(consulta);
        return consultaMapper.toResponseDTO(consultaAtualizada);
    }

    public void deletarConsulta(UUID id) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Consulta consulta = consultaRepository.findByIdAndUsuario(id, usuarioLogado)
                .orElseThrow(() -> new ConsultaNaoEncontradaException("Consulta nao encontrada"));
        consultaRepository.delete(consulta);
    }

}
