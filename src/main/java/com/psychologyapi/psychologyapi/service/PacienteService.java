package com.psychologyapi.psychologyapi.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.psychologyapi.psychologyapi.controller.dto.PacienteCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.PacienteResponseDTO;
import com.psychologyapi.psychologyapi.mapper.PacienteMapper;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PacienteService {
    
    private final PacienteRepository pacienteRepository;
    private final PacienteMapper pacienteMapper;
    private final SecurityService securityService;

    public PacienteResponseDTO cadastrar(PacienteCadastroDTO dto){
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteMapper.toEntity(dto);
        paciente.setUsuario(usuarioLogado); 
        Paciente pacienteSalvo = pacienteRepository.save(paciente);
        return pacienteMapper.toResponseDTO(pacienteSalvo);
    }

    public Optional<Paciente> buscarPorId(UUID id, Usuario usuario){
        return pacienteRepository.findByIdAndUsuario(id, usuario);
    }

    public List<Paciente> listarPacietes(String nome){
        return pacienteRepository.findByNomePacienteContainingIgnoreCase(nome);
    }

}
