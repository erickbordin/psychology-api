package com.psychologyapi.psychologyapi.service.aviso;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.model.CanalAviso;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.AvisoDeConsultaRepository;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.ResumoDiarioRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RegistradorDeAvisos {

    private static final long HORIZONTE_EM_HORAS = 31;
    private static final LocalTime HORA_DO_RESUMO = LocalTime.of(18, 0);

    private final ConsultaRepository consultaRepository;
    private final AvisoDeConsultaRepository avisoRepository;
    private final ResumoDiarioRepository resumoRepository;

    @Transactional
    public void registrarAvisosNovos(LocalDateTime agora) {
        for (Consulta consulta : consultaRepository
                .findByStatusAndDtConsultaGreaterThanAndDtConsultaLessThanEqualAndDeletedAtIsNull(
                        Status.AGENDADA, agora, agora.plusHours(HORIZONTE_EM_HORAS))) {
            if (!pacienteAceita(consulta.getPaciente())) {
                continue;
            }
            LocalDateTime momento = JanelaDeAviso.momento(consulta.getDtConsulta(),
                    consulta.getUsuario().getAntecedencia());
            if (!momento.isAfter(agora)) {
                avisoRepository.registrarSeNovo(UUID.randomUUID(), consulta.getId(), momento,
                        consulta.getDtConsulta(), agora);
            }
        }
    }

    @Transactional
    public void registrarResumos(LocalDateTime agora) {
        if (agora.toLocalTime().isBefore(HORA_DO_RESUMO)) {
            return;
        }
        LocalDate amanha = agora.toLocalDate().plusDays(1);
        for (Usuario usuario : consultaRepository.usuariosComResumoEntre(
                amanha.atStartOfDay(), amanha.plusDays(1).atStartOfDay())) {
            resumoRepository.registrarSeNovo(UUID.randomUUID(), usuario.getId(), amanha, agora);
        }
    }

    private boolean pacienteAceita(Paciente paciente) {
        return paciente.getDeletedAt() == null
                && paciente.getConsentimentoAvisoEm() != null
                && paciente.getCanalAviso() == CanalAviso.EMAIL
                && paciente.getEmailPaciente() != null
                && !paciente.getEmailPaciente().isBlank();
    }
}
