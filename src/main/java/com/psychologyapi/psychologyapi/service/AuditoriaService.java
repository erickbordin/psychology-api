package com.psychologyapi.psychologyapi.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.psychologyapi.psychologyapi.controller.dto.auditoria.LogAuditoriaResponseDTO;
import com.psychologyapi.psychologyapi.mapper.LogAuditoriaMapper;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.model.entity.LogAuditoria;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.LogAuditoriaRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final LogAuditoriaRepository logAuditoriaRepository;
    private final LogAuditoriaMapper logAuditoriaMapper;
    private final SecurityService securityService;

    public void registrar(Usuario usuario, AcaoAuditoria acao, String entidade, UUID entidadeId) {
        LogAuditoria log = new LogAuditoria();
        log.setUsuario(usuario);
        log.setAcao(acao);
        log.setEntidade(entidade);
        log.setEntidadeId(entidadeId);

        logAuditoriaRepository.save(log);
    }

    /**
     * A leitura da propria trilha nao gera log: listagem nunca e auditada por item
     * e nao existe id de entidade natural para registrar numa listagem.
     */
    public Page<LogAuditoriaResponseDTO> listar(Pageable pageable) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Page<LogAuditoria> logs = logAuditoriaRepository.findByUsuarioOrderByCreatedAtDesc(usuarioLogado, pageable);
        return logs.map(logAuditoriaMapper::toResponseDTO);
    }

}
