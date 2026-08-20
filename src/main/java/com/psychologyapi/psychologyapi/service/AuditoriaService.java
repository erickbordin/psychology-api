package com.psychologyapi.psychologyapi.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.model.entity.LogAuditoria;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.LogAuditoriaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final LogAuditoriaRepository logAuditoriaRepository;

    public void registrar(Usuario usuario, AcaoAuditoria acao, String entidade, UUID entidadeId) {
        LogAuditoria log = new LogAuditoria();
        log.setUsuario(usuario);
        log.setAcao(acao);
        log.setEntidade(entidade);
        log.setEntidadeId(entidadeId);

        logAuditoriaRepository.save(log);
    }
}