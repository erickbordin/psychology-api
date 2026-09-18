package com.psychologyapi.psychologyapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.psychologyapi.psychologyapi.controller.dto.painel.PainelHojeDTO;
import com.psychologyapi.psychologyapi.controller.dto.painel.TipoPendencia;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.AnotacaoRepository;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;
import com.psychologyapi.psychologyapi.support.Fixtures;

/**
 * F3 da revisao da Fatia 2: {@code PainelService.hoje()} rodava a MESMA consulta
 * ao banco duas vezes — "as consultas de hoje" servia para montar a linha do dia
 * e, logo abaixo, para achar as realizadas sem anotacao. Dois roundtrips
 * identicos por abertura da tela que o psicologo abre o dia inteiro.
 *
 * O numero de chamadas ao repositorio E o efeito aqui: nao existe saida HTTP que
 * distinga uma consulta de duas. Por isso o teste e de service, com Mockito, e
 * afirma {@code times(1)} sobre o metodo repetido — mantendo, junto, a
 * afirmacao de que as duas visoes derivadas continuam corretas.
 */
@ExtendWith(MockitoExtension.class)
class PainelServiceTest {

    @Mock private SecurityService securityService;
    @Mock private ConsultaRepository consultaRepository;
    @Mock private AnotacaoRepository anotacaoRepository;

    private PainelService painelService;

    private final Usuario logado = Fixtures.usuario("dona.painel@example.com");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 12, 2, 12, 0);

    private Consulta consultaDe(String nomeDoPaciente, LocalDateTime quando, Status status) {
        Paciente paciente = Fixtures.paciente(logado, nomeDoPaciente);
        paciente.setId(UUID.randomUUID());
        Consulta consulta = Fixtures.consulta(logado, paciente, quando, status);
        consulta.setId(UUID.randomUUID());
        return consulta;
    }

    @BeforeEach
    void preparar() {
        Clock relogioFixo = Clock.fixed(
                ZonedDateTime.of(AGORA, ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        painelService = new PainelService(securityService, consultaRepository, anotacaoRepository, relogioFixo);
    }

    @Test
    @DisplayName("hoje() le a agenda do dia UMA vez so, e deriva as duas visoes dela")
    void agendaDoDiaELidaUmaVezSo() {
        Consulta jaRealizadaSemAnotacao = consultaDe("Teodoro Alencar", AGORA.minusHours(3), Status.REALIZADA);
        Consulta aindaPorVir = consultaDe("Helena Vaz", AGORA.plusHours(2), Status.AGENDADA);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNullOrderByDtConsultaAsc(
                any(), any(), any()))
                .thenReturn(List.of(jaRealizadaSemAnotacao, aindaPorVir));
        when(consultaRepository.findByUsuarioAndStatusAndDtConsultaBeforeAndDeletedAtIsNullOrderByDtConsultaAsc(
                any(), any(), any()))
                .thenReturn(List.of());
        when(anotacaoRepository.idsDeConsultasComAnotacao(any(), anyList())).thenReturn(List.of());

        PainelHojeDTO painel = painelService.hoje();

        verify(consultaRepository, times(1))
                .findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNullOrderByDtConsultaAsc(any(), any(), any());

        assertThat(painel.sessoes()).hasSize(2);
        assertThat(painel.proxima().pacienteNome()).isEqualTo("Helena Vaz");
        assertThat(painel.pendencias())
                .singleElement()
                .satisfies(pendencia -> {
                    assertThat(pendencia.tipo()).isEqualTo(TipoPendencia.SEM_ANOTACAO);
                    assertThat(pendencia.pacienteNome()).isEqualTo("Teodoro Alencar");
                });
    }

    @Test
    @DisplayName("sessao realizada JA anotada nao entra na fila, e a agenda segue lida uma vez so")
    void realizadaAnotadaNaoEPendencia() {
        Consulta anotada = consultaDe("Nadia Furtado", AGORA.minusHours(5), Status.REALIZADA);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNullOrderByDtConsultaAsc(
                any(), any(), any()))
                .thenReturn(List.of(anotada));
        when(consultaRepository.findByUsuarioAndStatusAndDtConsultaBeforeAndDeletedAtIsNullOrderByDtConsultaAsc(
                any(), any(), any()))
                .thenReturn(List.of());
        when(anotacaoRepository.idsDeConsultasComAnotacao(any(), anyList()))
                .thenReturn(List.of(anotada.getId()));

        PainelHojeDTO painel = painelService.hoje();

        verify(consultaRepository, times(1))
                .findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNullOrderByDtConsultaAsc(any(), any(), any());
        assertThat(painel.pendencias()).isEmpty();
    }
}
