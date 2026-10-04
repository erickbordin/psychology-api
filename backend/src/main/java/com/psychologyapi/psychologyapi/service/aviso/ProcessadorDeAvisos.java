package com.psychologyapi.psychologyapi.service.aviso;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.model.AvisoDeConsulta;
import com.psychologyapi.psychologyapi.model.CanalAviso;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.EstadoAviso;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.ResumoDiario;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.AvisoDeConsultaRepository;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.ResumoDiarioRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProcessadorDeAvisos {

    private static final int MAXIMO_DE_TENTATIVAS = 3;

    private final AvisoDeConsultaRepository avisoRepository;
    private final ResumoDiarioRepository resumoRepository;
    private final ConsultaRepository consultaRepository;
    private final Carteiro carteiro;
    private final RedatorDeEmail redator;

    @FunctionalInterface
    private interface Fechamento {
        int fechar(UUID id, EstadoAviso estado, LocalDateTime enviadoEm, String erro, LocalDateTime proxima);
    }

    @Transactional
    public void processarAviso(UUID avisoId, LocalDateTime agora) {
        AvisoDeConsulta aviso = avisoRepository.findById(avisoId).orElse(null);
        if (aviso == null || aviso.getEstado() != EstadoAviso.PENDENTE) {
            return;
        }
        int lidas = aviso.getTentativas();
        if (avisoRepository.reivindicarTentativa(avisoId, lidas) == 0) {
            return;
        }
        int tentativa = lidas + 1;
        aviso = avisoRepository.findById(avisoId).orElseThrow();
        Consulta consulta = aviso.getConsulta();
        if (!aindaDeveSair(aviso, consulta, agora)) {
            avisoRepository.fechar(avisoId, EstadoAviso.CANCELADO, null, null, null);
            return;
        }
        try {
            carteiro.enviar(redator.lembrete(consulta));
            avisoRepository.fechar(avisoId, EstadoAviso.ENVIADO, agora, null, null);
        } catch (RuntimeException e) {
            registrarFalha(avisoId, tentativa, e, agora, avisoRepository::fechar);
        }
    }

    @Transactional
    public void processarResumo(UUID resumoId, LocalDateTime agora) {
        ResumoDiario resumo = resumoRepository.findById(resumoId).orElse(null);
        if (resumo == null || resumo.getEstado() != EstadoAviso.PENDENTE) {
            return;
        }
        int lidas = resumo.getTentativas();
        if (resumoRepository.reivindicarTentativa(resumoId, lidas) == 0) {
            return;
        }
        int tentativa = lidas + 1;
        resumo = resumoRepository.findById(resumoId).orElseThrow();
        Usuario usuario = resumo.getUsuario();
        LocalDate dia = resumo.getData();
        List<Consulta> sessoes = agora.toLocalDate().isBefore(dia) && usuario.isResumoDiario()
                ? consultaRepository
                        .findByUsuarioAndStatusAndDtConsultaGreaterThanEqualAndDtConsultaLessThanAndDeletedAtIsNullOrderByDtConsultaAsc(
                                usuario, Status.AGENDADA, dia.atStartOfDay(), dia.plusDays(1).atStartOfDay())
                : List.of();
        if (sessoes.isEmpty()) {
            resumoRepository.fechar(resumoId, EstadoAviso.CANCELADO, null, null, null);
            return;
        }
        try {
            carteiro.enviar(redator.resumo(usuario, dia, sessoes));
            resumoRepository.fechar(resumoId, EstadoAviso.ENVIADO, agora, null, null);
        } catch (RuntimeException e) {
            registrarFalha(resumoId, tentativa, e, agora, resumoRepository::fechar);
        }
    }

    private boolean aindaDeveSair(AvisoDeConsulta aviso, Consulta consulta, LocalDateTime agora) {
        Paciente paciente = consulta.getPaciente();
        return consulta.getDeletedAt() == null
                && consulta.getStatus() == Status.AGENDADA
                && consulta.getDtConsulta().equals(aviso.getDataHoraConsulta())
                && consulta.getDtConsulta().isAfter(agora)
                && paciente.getDeletedAt() == null
                && paciente.getConsentimentoAvisoEm() != null
                && paciente.getCanalAviso() == CanalAviso.EMAIL
                && paciente.getEmailPaciente() != null
                && !paciente.getEmailPaciente().isBlank();
    }

    private void registrarFalha(UUID id, int tentativa, RuntimeException e, LocalDateTime agora, Fechamento fechamento) {
        String erro = e.getClass().getSimpleName();
        if (tentativa >= MAXIMO_DE_TENTATIVAS) {
            fechamento.fechar(id, EstadoAviso.FALHOU, null, erro, null);
            return;
        }
        fechamento.fechar(id, EstadoAviso.PENDENTE, null, erro, agora.plusMinutes(tentativa == 1 ? 5 : 15));
    }
}
