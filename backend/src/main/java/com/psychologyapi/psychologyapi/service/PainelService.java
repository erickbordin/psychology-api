package com.psychologyapi.psychologyapi.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.controller.dto.painel.PainelHojeDTO;
import com.psychologyapi.psychologyapi.controller.dto.painel.PainelMesDTO;
import com.psychologyapi.psychologyapi.controller.dto.painel.PendenciaDoPainelDTO;
import com.psychologyapi.psychologyapi.controller.dto.painel.SessaoDoDiaDTO;
import com.psychologyapi.psychologyapi.controller.dto.painel.TipoPendencia;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.AnotacaoRepository;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.projecao.ContagemDoMes;
import com.psychologyapi.psychologyapi.service.periodo.MesConsultado;
import com.psychologyapi.psychologyapi.service.security.SecurityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PainelService {

    private final SecurityService securityService;
    private final ConsultaRepository consultaRepository;
    private final AnotacaoRepository anotacaoRepository;
    private final Clock clock;

    /** A tela (1) inteira numa chamada (spec 5.6).
     *
     *  readOnly porque monta DTO a partir de Consulta.paciente, que e LAZY:
     *  sem transacao o acesso ao nome vira LazyInitializationException. */
    @Transactional(readOnly = true)
    public PainelHojeDTO hoje() {
        Usuario usuario = securityService.obterUsuarioLogado();

        // com o fuso unico da spec 5.9 fixado no Dockerfile, "hoje" aqui e o
        // mesmo dia que o psicologo ve no relogio dele
        LocalDateTime agora = LocalDateTime.now(clock);
        LocalDateTime inicioDoDia = agora.toLocalDate().atStartOfDay();
        LocalDateTime fimDoDia = agora.toLocalDate().atTime(LocalTime.MAX);

        // A agenda de hoje e lida UMA vez e as duas visoes saem dela (a linha do
        // dia e a fila de "realizada sem anotacao"). Ate a revisao da Fatia 2
        // eram duas chamadas identicas ao mesmo metodo, com os mesmos tres
        // argumentos — dois roundtrips por abertura de uma tela que o psicologo
        // deixa aberta o dia inteiro, e duas fotos do banco que podiam divergir
        // entre si.
        List<Consulta> agendaDeHoje = consultaRepository
                .findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNullOrderByDtConsultaAsc(
                        usuario, inicioDoDia, fimDoDia);

        List<SessaoDoDiaDTO> sessoes = agendaDeHoje
                .stream()
                // cancelada nao e compromisso: nao ocupa a linha do dia
                .filter(c -> c.getStatus() != Status.CANCELADA)
                .map(c -> new SessaoDoDiaDTO(
                        c.getId(),
                        c.getDtConsulta(),
                        c.getPaciente().getId(),
                        c.getPaciente().getNomePaciente(),
                        c.getStatus(),
                        c.getValor(),
                        c.getPagoEm()))
                .toList();

        // a proxima e a primeira que ainda nao comecou E continua agendada:
        // sessao ja marcada como realizada nao volta a ser "a proxima"
        SessaoDoDiaDTO proxima = sessoes.stream()
                .filter(s -> s.dtConsulta().isAfter(agora) && s.status() == Status.AGENDADA)
                .findFirst()
                .orElse(null);

        List<PendenciaDoPainelDTO> semStatus = consultaRepository
                .findByUsuarioAndStatusAndDtConsultaBeforeAndDeletedAtIsNullOrderByDtConsultaAsc(
                        usuario, Status.AGENDADA, agora)
                .stream()
                .map(c -> new PendenciaDoPainelDTO(
                        c.getId(), c.getDtConsulta(), c.getPaciente().getNomePaciente(),
                        TipoPendencia.SEM_STATUS))
                .toList();

        // "realizada sem anotacao" (spec 9, Fatia 2). So as de HOJE: a fila do
        // painel e do dia, e varrer o historico inteiro transformaria a tela
        // numa lista de tudo que nunca foi escrito. Deriva da MESMA leitura da
        // agenda usada acima.
        List<Consulta> realizadasHoje = agendaDeHoje
                .stream()
                .filter(c -> c.getStatus() == Status.REALIZADA)
                .toList();

        // Set, e nao List: o filtro abaixo pergunta "esta neste conjunto?" uma
        // vez por sessao realizada, e `List.contains` varre a lista toda a cada
        // pergunta (O(n*m)). A pergunta e de pertencimento, entao a estrutura e
        // de pertencimento.
        //
        // A guarda de lista vazia nao e paranoia: `IN ()` e SQL invalido no
        // Postgres, e o dia 1 do psicologo cai exatamente neste caminho.
        Set<UUID> jaAnotadas = realizadasHoje.isEmpty()
                ? Set.of()
                : Set.copyOf(anotacaoRepository.idsDeConsultasComAnotacao(
                        usuario, realizadasHoje.stream().map(Consulta::getId).toList()));

        List<PendenciaDoPainelDTO> semAnotacao = realizadasHoje.stream()
                .filter(c -> !jaAnotadas.contains(c.getId()))
                .map(c -> new PendenciaDoPainelDTO(
                        c.getId(), c.getDtConsulta(), c.getPaciente().getNomePaciente(),
                        TipoPendencia.SEM_ANOTACAO))
                .toList();

        List<PendenciaDoPainelDTO> semBaixa = realizadasHoje.stream()
                .filter(c -> c.getValor() != null && c.getPagoEm() == null)
                .map(c -> new PendenciaDoPainelDTO(
                        c.getId(), c.getDtConsulta(), c.getPaciente().getNomePaciente(),
                        TipoPendencia.SEM_BAIXA))
                .toList();

        List<PendenciaDoPainelDTO> pendencias = Stream.of(semStatus, semAnotacao, semBaixa)
                .flatMap(List::stream)
                .sorted(Comparator.comparing(PendenciaDoPainelDTO::dtConsulta))
                .toList();

        return new PainelHojeDTO(proxima, sessoes, pendencias);
    }

    @Transactional(readOnly = true)
    public PainelMesDTO mes(Integer ano, Integer mes) {
        MesConsultado periodo = MesConsultado.resolver(ano, mes, clock);
        Usuario usuario = securityService.obterUsuarioLogado();
        ContagemDoMes contagem = consultaRepository.contagemDoMes(
                usuario, periodo.inicio(), periodo.fimExclusivo());

        long realizadas = contagem.getRealizadas();
        long faltas = contagem.getFaltas();
        long denominador = realizadas + faltas;
        BigDecimal taxaDeFalta = denominador == 0
                ? null
                : BigDecimal.valueOf(faltas).divide(BigDecimal.valueOf(denominador), 4, RoundingMode.HALF_UP);

        return new PainelMesDTO(periodo.ano(), periodo.mes(), realizadas, faltas,
                contagem.getCanceladas(), taxaDeFalta, contagem.getPacientesAtivos());
    }
}
