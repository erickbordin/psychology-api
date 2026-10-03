package com.psychologyapi.psychologyapi.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.controller.dto.financeiro.DevedorDTO;
import com.psychologyapi.psychologyapi.controller.dto.financeiro.FinanceiroMesDTO;
import com.psychologyapi.psychologyapi.controller.dto.financeiro.SessaoFinanceiraDTO;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.projecao.TotaisFinanceirosDoMes;
import com.psychologyapi.psychologyapi.service.periodo.MesConsultado;
import com.psychologyapi.psychologyapi.service.security.SecurityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FinanceiroService {

    private final SecurityService securityService;
    private final ConsultaRepository consultaRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public FinanceiroMesDTO doMes(Integer ano, Integer mes) {
        MesConsultado periodo = MesConsultado.resolver(ano, mes, clock);
        Usuario usuario = securityService.obterUsuarioLogado();

        TotaisFinanceirosDoMes totais = consultaRepository.totaisFinanceirosDoMes(
                usuario, periodo.inicio(), periodo.fimExclusivo());
        BigDecimal faturado = emReais(totais.getFaturado());
        BigDecimal recebido = emReais(totais.getRecebido());

        List<SessaoFinanceiraDTO> sessoes = consultaRepository
                .findByUsuarioAndStatusAndDtConsultaGreaterThanEqualAndDtConsultaLessThanAndDeletedAtIsNullOrderByDtConsultaAsc(
                        usuario, Status.REALIZADA, periodo.inicio(), periodo.fimExclusivo())
                .stream()
                .map(c -> new SessaoFinanceiraDTO(
                        c.getId(),
                        c.getDtConsulta(),
                        c.getPaciente().getId(),
                        c.getPaciente().getNomePaciente(),
                        c.getPaciente().getDeletedAt() != null,
                        c.getValor() == null ? null : emReais(c.getValor()),
                        c.getPagoEm()))
                .toList();

        return new FinanceiroMesDTO(
                periodo.ano(),
                periodo.mes(),
                faturado,
                recebido,
                faturado.subtract(recebido),
                totais.getSemValor() == null ? 0 : totais.getSemValor().intValue(),
                sessoes,
                devendo(usuario));
    }

    private List<DevedorDTO> devendo(Usuario usuario) {
        Map<UUID, List<Consulta>> porPaciente = new LinkedHashMap<>();
        consultaRepository
                .findByUsuarioAndStatusAndValorIsNotNullAndPagoEmIsNullAndDeletedAtIsNullOrderByDtConsultaAsc(
                        usuario, Status.REALIZADA)
                .forEach(c -> porPaciente.computeIfAbsent(c.getPaciente().getId(), id -> new ArrayList<>()).add(c));

        return porPaciente.values().stream()
                .map(consultas -> {
                    Paciente paciente = consultas.get(0).getPaciente();
                    BigDecimal total = consultas.stream()
                            .map(Consulta::getValor)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new DevedorDTO(
                            paciente.getId(),
                            paciente.getNomePaciente(),
                            paciente.getDeletedAt() != null,
                            consultas.size(),
                            emReais(total),
                            consultas.stream().map(Consulta::getId).toList());
                })
                .sorted(Comparator.comparing(DevedorDTO::total).reversed()
                        .thenComparing(DevedorDTO::pacienteNome))
                .toList();
    }

    private static BigDecimal emReais(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
    }
}
