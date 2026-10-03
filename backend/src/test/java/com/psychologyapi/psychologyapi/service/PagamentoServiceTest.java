package com.psychologyapi.psychologyapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.psychologyapi.psychologyapi.controller.dto.consulta.BaixaEmLoteDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ValorDaConsultaDTO;
import com.psychologyapi.psychologyapi.exceptions.BaixaInvalidaException;
import com.psychologyapi.psychologyapi.exceptions.ConsultaNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.SessaoComBaixaException;
import com.psychologyapi.psychologyapi.mapper.ConsultaMapper;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;
import com.psychologyapi.psychologyapi.support.Fixtures;

@ExtendWith(MockitoExtension.class)
class PagamentoServiceTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 12, 2, 16, 30);

    @Mock private ConsultaRepository consultaRepository;
    @Mock private ConsultaMapper consultaMapper;
    @Mock private SecurityService securityService;
    @Mock private AuditoriaService auditoriaService;

    private PagamentoService pagamentoService;
    private final Usuario logado = Fixtures.usuario("dona.pagamento@example.com");

    @BeforeEach
    void preparar() {
        ZoneId fuso = ZoneId.of("America/Sao_Paulo");
        Clock relogio = Clock.fixed(AGORA.atZone(fuso).toInstant(), fuso);
        pagamentoService = new PagamentoService(
                consultaRepository, consultaMapper, securityService, auditoriaService, relogio);
    }

    private Consulta consulta(Status status, String valor, LocalDateTime pagoEm) {
        Paciente paciente = Fixtures.paciente(logado, "Bruna Teixeira");
        paciente.setId(UUID.randomUUID());
        Consulta consulta = Fixtures.consulta(logado, paciente, AGORA.minusDays(1), status);
        consulta.setId(UUID.randomUUID());
        consulta.setValor(valor == null ? null : new BigDecimal(valor));
        consulta.setPagoEm(pagoEm);
        return consulta;
    }

    private void encontrada(Consulta consulta) {
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(consulta.getId(), logado))
                .thenReturn(Optional.of(consulta));
    }

    @Test
    @DisplayName("darBaixa grava o agora do relogio e audita depois do save")
    void darBaixaGravaAgora() {
        Consulta consulta = consulta(Status.REALIZADA, "180.00", null);
        encontrada(consulta);
        when(consultaRepository.save(consulta)).thenReturn(consulta);

        pagamentoService.darBaixa(consulta.getId());

        assertThat(consulta.getPagoEm()).isEqualTo(AGORA);
        InOrder ordem = inOrder(consultaRepository, auditoriaService);
        ordem.verify(consultaRepository).save(consulta);
        ordem.verify(auditoriaService).registrar(logado, AcaoAuditoria.ATUALIZACAO, "Consulta", consulta.getId());
    }

    @Test
    @DisplayName("darBaixa numa sessao ja paga nao muda a data, nao salva e nao audita")
    void darBaixaIdempotente() {
        LocalDateTime antes = AGORA.minusDays(3);
        Consulta consulta = consulta(Status.REALIZADA, "180.00", antes);
        encontrada(consulta);

        pagamentoService.darBaixa(consulta.getId());

        assertThat(consulta.getPagoEm()).isEqualTo(antes);
        verify(consultaRepository, never()).save(any());
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("darBaixa em sessao nao realizada: 409 com a frase certa")
    void darBaixaEmAgendada() {
        Consulta consulta = consulta(Status.AGENDADA, "180.00", null);
        encontrada(consulta);

        assertThatThrownBy(() -> pagamentoService.darBaixa(consulta.getId()))
                .isInstanceOf(BaixaInvalidaException.class)
                .hasMessage("Só sessão realizada recebe baixa. Marque a sessão como realizada antes.");
        verify(consultaRepository, never()).save(any());
    }

    @Test
    @DisplayName("darBaixa em realizada sem valor: 409 pedindo o valor")
    void darBaixaSemValor() {
        Consulta consulta = consulta(Status.REALIZADA, null, null);
        encontrada(consulta);

        assertThatThrownBy(() -> pagamentoService.darBaixa(consulta.getId()))
                .isInstanceOf(BaixaInvalidaException.class)
                .hasMessage("Defina o valor antes de dar baixa.");
    }

    @Test
    @DisplayName("darBaixa em consulta de outro psicologo: nao encontrada")
    void darBaixaDeOutro() {
        UUID id = UUID.randomUUID();
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, logado)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pagamentoService.darBaixa(id))
                .isInstanceOf(ConsultaNaoEncontradaException.class);
    }

    @Test
    @DisplayName("desfazerBaixa limpa pagoEm, salva e audita")
    void desfazer() {
        Consulta consulta = consulta(Status.REALIZADA, "180.00", AGORA.minusDays(1));
        encontrada(consulta);
        when(consultaRepository.save(consulta)).thenReturn(consulta);

        pagamentoService.desfazerBaixa(consulta.getId());

        assertThat(consulta.getPagoEm()).isNull();
        verify(auditoriaService).registrar(logado, AcaoAuditoria.ATUALIZACAO, "Consulta", consulta.getId());
    }

    @Test
    @DisplayName("desfazerBaixa sem baixa nao salva nem audita")
    void desfazerSemBaixa() {
        Consulta consulta = consulta(Status.REALIZADA, "180.00", null);
        encontrada(consulta);

        pagamentoService.desfazerBaixa(consulta.getId());

        verify(consultaRepository, never()).save(any());
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("lote da baixa nas abertas, pula as pagas, conta ids repetidos uma vez, mesmo agora para todas")
    void loteFeliz() {
        Consulta aberta1 = consulta(Status.REALIZADA, "180.00", null);
        Consulta aberta2 = consulta(Status.REALIZADA, "180.00", null);
        Consulta jaPaga = consulta(Status.REALIZADA, "180.00", AGORA.minusDays(5));
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByIdInAndUsuarioAndDeletedAtIsNull(anyList(), any()))
                .thenReturn(List.of(aberta1, aberta2, jaPaga));
        when(consultaRepository.saveAll(anyList())).thenAnswer(invocacao -> invocacao.getArgument(0));

        var resposta = pagamentoService.darBaixaEmLote(new BaixaEmLoteDTO(List.of(
                aberta1.getId(), aberta2.getId(), jaPaga.getId(), aberta1.getId())));

        assertThat(resposta.consultasBaixadas()).isEqualTo(2);
        assertThat(aberta1.getPagoEm()).isEqualTo(AGORA);
        assertThat(aberta2.getPagoEm()).isEqualTo(AGORA);
        assertThat(jaPaga.getPagoEm()).isEqualTo(AGORA.minusDays(5));
        verify(auditoriaService, times(2)).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("lote com um id que nao e do psicologo: 404 e nada gravado")
    void loteComIdDeOutro() {
        Consulta aberta = consulta(Status.REALIZADA, "180.00", null);
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByIdInAndUsuarioAndDeletedAtIsNull(anyList(), any()))
                .thenReturn(List.of(aberta));

        assertThatThrownBy(() -> pagamentoService.darBaixaEmLote(
                new BaixaEmLoteDTO(List.of(aberta.getId(), UUID.randomUUID()))))
                .isInstanceOf(ConsultaNaoEncontradaException.class);
        assertThat(aberta.getPagoEm()).isNull();
        verify(consultaRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("lote com uma sessao sem valor: 409 e NENHUMA ganha baixa")
    void loteTudoOuNada() {
        Consulta aberta = consulta(Status.REALIZADA, "180.00", null);
        Consulta semValor = consulta(Status.REALIZADA, null, null);
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByIdInAndUsuarioAndDeletedAtIsNull(anyList(), any()))
                .thenReturn(List.of(aberta, semValor));

        assertThatThrownBy(() -> pagamentoService.darBaixaEmLote(
                new BaixaEmLoteDTO(List.of(aberta.getId(), semValor.getId()))))
                .isInstanceOf(BaixaInvalidaException.class)
                .hasMessage("Defina o valor antes de dar baixa.");
        assertThat(aberta.getPagoEm()).isNull();
        verify(consultaRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("alterarValor troca o valor de uma realizada sem baixa")
    void alterarValor() {
        Consulta consulta = consulta(Status.REALIZADA, "180.00", null);
        encontrada(consulta);
        when(consultaRepository.save(consulta)).thenReturn(consulta);

        pagamentoService.alterarValor(consulta.getId(), new ValorDaConsultaDTO(new BigDecimal("90.00")));

        assertThat(consulta.getValor()).isEqualByComparingTo("90.00");
        verify(auditoriaService).registrar(logado, AcaoAuditoria.ATUALIZACAO, "Consulta", consulta.getId());
    }

    @Test
    @DisplayName("alterarValor numa sessao paga: 409 mandando desfazer a baixa")
    void alterarValorPaga() {
        Consulta consulta = consulta(Status.REALIZADA, "180.00", AGORA.minusDays(1));
        encontrada(consulta);

        assertThatThrownBy(() -> pagamentoService.alterarValor(
                consulta.getId(), new ValorDaConsultaDTO(new BigDecimal("90.00"))))
                .isInstanceOf(SessaoComBaixaException.class)
                .hasMessage("Esta sessão já tem baixa. Desfaça a baixa antes de alterar o valor.");
        assertThat(consulta.getValor()).isEqualByComparingTo("180.00");
    }

    @Test
    @DisplayName("alterarValor numa sessao agendada: 409")
    void alterarValorAgendada() {
        Consulta consulta = consulta(Status.AGENDADA, null, null);
        encontrada(consulta);

        assertThatThrownBy(() -> pagamentoService.alterarValor(
                consulta.getId(), new ValorDaConsultaDTO(new BigDecimal("90.00"))))
                .isInstanceOf(BaixaInvalidaException.class)
                .hasMessage("Só sessão realizada tem valor a alterar. Marque a sessão como realizada antes.");
    }
}
