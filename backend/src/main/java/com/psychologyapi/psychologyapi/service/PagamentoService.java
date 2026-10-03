package com.psychologyapi.psychologyapi.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.controller.dto.consulta.BaixaEmLoteDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.BaixaEmLoteResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ValorDaConsultaDTO;
import com.psychologyapi.psychologyapi.exceptions.BaixaInvalidaException;
import com.psychologyapi.psychologyapi.exceptions.ConsultaNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.SessaoComBaixaException;
import com.psychologyapi.psychologyapi.mapper.ConsultaMapper;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PagamentoService {

    private final ConsultaRepository consultaRepository;
    private final ConsultaMapper consultaMapper;
    private final SecurityService securityService;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    @Transactional
    public ConsultaResponseDTO darBaixa(UUID consultaId) {
        Usuario usuario = securityService.obterUsuarioLogado();
        Consulta consulta = buscar(consultaId, usuario);
        if (consulta.getPagoEm() != null) {
            return consultaMapper.toResponseDTO(consulta);
        }
        exigirQuePodeReceberBaixa(consulta);
        consulta.setPagoEm(LocalDateTime.now(clock));
        return salvarEAuditar(consulta, usuario);
    }

    @Transactional
    public ConsultaResponseDTO desfazerBaixa(UUID consultaId) {
        Usuario usuario = securityService.obterUsuarioLogado();
        Consulta consulta = buscar(consultaId, usuario);
        if (consulta.getPagoEm() == null) {
            return consultaMapper.toResponseDTO(consulta);
        }
        consulta.setPagoEm(null);
        return salvarEAuditar(consulta, usuario);
    }

    @Transactional
    public BaixaEmLoteResponseDTO darBaixaEmLote(BaixaEmLoteDTO dto) {
        Usuario usuario = securityService.obterUsuarioLogado();
        List<UUID> ids = dto.consultaIds().stream().distinct().toList();
        List<Consulta> consultas = consultaRepository.findByIdInAndUsuarioAndDeletedAtIsNull(ids, usuario);
        if (consultas.size() != ids.size()) {
            throw new ConsultaNaoEncontradaException("Consulta nao encontrada");
        }

        List<Consulta> aBaixar = consultas.stream().filter(c -> c.getPagoEm() == null).toList();
        aBaixar.forEach(this::exigirQuePodeReceberBaixa);

        LocalDateTime agora = LocalDateTime.now(clock);
        aBaixar.forEach(c -> c.setPagoEm(agora));
        List<Consulta> salvas = consultaRepository.saveAll(aBaixar);
        salvas.forEach(c -> auditoriaService.registrar(usuario, AcaoAuditoria.ATUALIZACAO, "Consulta", c.getId()));
        return new BaixaEmLoteResponseDTO(salvas.size());
    }

    @Transactional
    public ConsultaResponseDTO alterarValor(UUID consultaId, ValorDaConsultaDTO dto) {
        Usuario usuario = securityService.obterUsuarioLogado();
        Consulta consulta = buscar(consultaId, usuario);
        if (consulta.getStatus() != Status.REALIZADA) {
            throw new BaixaInvalidaException(BaixaInvalidaException.SO_REALIZADA_TEM_VALOR);
        }
        if (consulta.getPagoEm() != null) {
            throw new SessaoComBaixaException(SessaoComBaixaException.AO_ALTERAR_VALOR);
        }
        consulta.setValor(dto.valor());
        return salvarEAuditar(consulta, usuario);
    }

    private Consulta buscar(UUID consultaId, Usuario usuario) {
        return consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(consultaId, usuario)
                .orElseThrow(() -> new ConsultaNaoEncontradaException("Consulta nao encontrada"));
    }

    private void exigirQuePodeReceberBaixa(Consulta consulta) {
        if (consulta.getStatus() != Status.REALIZADA) {
            throw new BaixaInvalidaException(BaixaInvalidaException.SO_REALIZADA_RECEBE_BAIXA);
        }
        if (consulta.getValor() == null) {
            throw new BaixaInvalidaException(BaixaInvalidaException.SEM_VALOR);
        }
    }

    private ConsultaResponseDTO salvarEAuditar(Consulta consulta, Usuario usuario) {
        Consulta salva = consultaRepository.save(consulta);
        auditoriaService.registrar(usuario, AcaoAuditoria.ATUALIZACAO, "Consulta", salva.getId());
        return consultaMapper.toResponseDTO(salva);
    }
}
