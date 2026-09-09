package com.psychologyapi.psychologyapi.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.controller.dto.painel.PainelHojeDTO;
import com.psychologyapi.psychologyapi.controller.dto.painel.PendenciaDoPainelDTO;
import com.psychologyapi.psychologyapi.controller.dto.painel.SessaoDoDiaDTO;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PainelService {

    private final SecurityService securityService;
    private final ConsultaRepository consultaRepository;
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

        List<SessaoDoDiaDTO> sessoes = consultaRepository
                .findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNullOrderByDtConsultaAsc(
                        usuario, inicioDoDia, fimDoDia)
                .stream()
                // cancelada nao e compromisso: nao ocupa a linha do dia
                .filter(c -> c.getStatus() != Status.CANCELADA)
                .map(c -> new SessaoDoDiaDTO(
                        c.getId(),
                        c.getDtConsulta(),
                        c.getPaciente().getId(),
                        c.getPaciente().getNomePaciente(),
                        c.getStatus()))
                .toList();

        // a proxima e a primeira que ainda nao comecou E continua agendada:
        // sessao ja marcada como realizada nao volta a ser "a proxima"
        SessaoDoDiaDTO proxima = sessoes.stream()
                .filter(s -> s.dtConsulta().isAfter(agora) && s.status() == Status.AGENDADA)
                .findFirst()
                .orElse(null);

        List<PendenciaDoPainelDTO> pendencias = consultaRepository
                .findByUsuarioAndStatusAndDtConsultaBeforeAndDeletedAtIsNullOrderByDtConsultaAsc(
                        usuario, Status.AGENDADA, agora)
                .stream()
                .map(c -> new PendenciaDoPainelDTO(
                        c.getId(), c.getDtConsulta(), c.getPaciente().getNomePaciente()))
                .toList();

        return new PainelHojeDTO(proxima, sessoes, pendencias);
    }
}
