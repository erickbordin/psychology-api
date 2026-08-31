package com.psychologyapi.psychologyapi.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.psychologyapi.psychologyapi.controller.dto.auditoria.LogAuditoriaResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.FiltroAuditoriaInvalidoException;
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
     *
     * entidadeId sem entidade e rejeitado: id_entidade nao e unico entre os tipos
     * de entidade desta trilha, entao filtrar so pelo id misturaria, por exemplo,
     * o historico de um paciente com o de uma consulta que caiu no mesmo UUID.
     */
    public Page<LogAuditoriaResponseDTO> listar(Pageable pageable, String entidade, UUID entidadeId) {
        if (entidade == null && entidadeId != null) {
            throw new FiltroAuditoriaInvalidoException("entidadeId nao pode ser informado sem entidade");
        }

        Usuario usuarioLogado = securityService.obterUsuarioLogado();

        Page<LogAuditoria> logs;
        if (entidade != null && entidadeId != null) {
            logs = logAuditoriaRepository.findByUsuarioAndEntidadeAndEntidadeIdOrderByCreatedAtDesc(usuarioLogado,
                    entidade, entidadeId, pageable);
        } else if (entidade != null) {
            logs = logAuditoriaRepository.findByUsuarioAndEntidadeOrderByCreatedAtDesc(usuarioLogado, entidade,
                    pageable);
        } else {
            logs = logAuditoriaRepository.findByUsuarioOrderByCreatedAtDesc(usuarioLogado, pageable);
        }

        return logs.map(logAuditoriaMapper::toResponseDTO);
    }

}
