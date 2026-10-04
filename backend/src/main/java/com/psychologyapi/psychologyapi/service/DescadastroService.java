package com.psychologyapi.psychologyapi.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.controller.dto.paciente.DescadastroDTO;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.model.CanalAviso;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DescadastroService {

    private final PacienteRepository pacienteRepository;
    private final AuditoriaService auditoriaService;

    public DescadastroDTO consultar(String token) {
        return new DescadastroDTO(estaAtivo(buscar(token)));
    }

    @Transactional
    public DescadastroDTO descadastrar(String token) {
        Paciente paciente = buscar(token);
        if (estaAtivo(paciente)) {
            paciente.setCanalAviso(CanalAviso.NENHUM);
            paciente.setConsentimentoAvisoEm(null);
            paciente.setDescadastradoEm(LocalDateTime.now());
            pacienteRepository.save(paciente);
            auditoriaService.registrar(paciente.getUsuario(), AcaoAuditoria.ATUALIZACAO, "DescadastroDoPaciente",
                    paciente.getId());
        }
        return new DescadastroDTO(false);
    }

    private Paciente buscar(String token) {
        return pacienteRepository.findByTokenDescadastroAndDeletedAtIsNull(token)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Link invalido"));
    }

    private boolean estaAtivo(Paciente paciente) {
        return paciente.getConsentimentoAvisoEm() != null && paciente.getCanalAviso() == CanalAviso.EMAIL;
    }
}
