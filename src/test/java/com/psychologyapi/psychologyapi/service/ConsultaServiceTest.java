package com.psychologyapi.psychologyapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaAtualizacaoDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaRecorrenteCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.SerieCancelamentoResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.SerieConsultaResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.ConflitoDeHorarioException;
import com.psychologyapi.psychologyapi.exceptions.ConsultaNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.exceptions.PeriodoInvalidoException;
import com.psychologyapi.psychologyapi.mapper.ConsultaMapper;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;
import com.psychologyapi.psychologyapi.support.Fixtures;

@ExtendWith(MockitoExtension.class)
class ConsultaServiceTest {

    @Mock
    private ConsultaMapper consultaMapper;
    @Mock
    private ConsultaRepository consultaRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private SecurityService securityService;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private ConsultaService consultaService;

    private final Usuario logado = Fixtures.usuario("dono@teste.com");
    private final LocalDateTime quando = LocalDateTime.of(2026, 9, 10, 14, 0);

    private Paciente pacienteDoLogado(UUID pacienteId) {
        Paciente paciente = Fixtures.paciente(logado, "Ana");
        paciente.setId(pacienteId);
        return paciente;
    }

    @Test
    @DisplayName("cadastrar: paciente de outro usuario responde 404 e nao agenda nada")
    void cadastrarComPacienteDeOutroUsuario() {
        UUID pacienteId = UUID.randomUUID();
        ConsultaCadastroDTO dto = new ConsultaCadastroDTO(pacienteId, quando);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consultaService.cadastrar(dto))
                .isInstanceOf(PacienteNaoEncontradoException.class);

        verify(consultaRepository, never()).save(any());
    }

    @Test
    @DisplayName("cadastrar: janela de conflito e de 50 minutos para cada lado do horario")
    void janelaDeConflitoEDeCinquentaMinutos() {
        UUID pacienteId = UUID.randomUUID();
        ConsultaCadastroDTO dto = new ConsultaCadastroDTO(pacienteId, quando);
        Consulta nova = Fixtures.consulta(null, null, quando, Status.AGENDADA);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado))
                .thenReturn(Optional.of(pacienteDoLogado(pacienteId)));
        when(consultaMapper.toEntity(dto)).thenReturn(nova);
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), any(), any()))
                .thenReturn(List.of());
        when(consultaRepository.save(nova)).thenReturn(nova);

        consultaService.cadastrar(dto);

        ArgumentCaptor<LocalDateTime> inicio = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> fim = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(consultaRepository).findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), inicio.capture(),
                fim.capture());

        assertThat(inicio.getValue()).isEqualTo(quando.minusMinutes(50));
        assertThat(fim.getValue()).isEqualTo(quando.plusMinutes(50));
    }

    @Test
    @DisplayName("cadastrar: consulta ativa na janela gera conflito")
    void cadastrarComConflitoLanca() {
        UUID pacienteId = UUID.randomUUID();
        ConsultaCadastroDTO dto = new ConsultaCadastroDTO(pacienteId, quando);
        Consulta existente = Fixtures.consulta(logado, null, quando.plusMinutes(20), Status.AGENDADA);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado))
                .thenReturn(Optional.of(pacienteDoLogado(pacienteId)));
        when(consultaMapper.toEntity(dto)).thenReturn(Fixtures.consulta(null, null, quando, Status.AGENDADA));
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), any(), any()))
                .thenReturn(List.of(existente));

        assertThatThrownBy(() -> consultaService.cadastrar(dto))
                .isInstanceOf(ConflitoDeHorarioException.class);

        verify(consultaRepository, never()).save(any());
    }

    @Test
    @DisplayName("cadastrar: consulta CANCELADA na janela nao bloqueia o agendamento")
    void canceladaNaoGeraConflito() {
        UUID pacienteId = UUID.randomUUID();
        ConsultaCadastroDTO dto = new ConsultaCadastroDTO(pacienteId, quando);
        Consulta nova = Fixtures.consulta(null, null, quando, Status.AGENDADA);
        Consulta cancelada = Fixtures.consulta(logado, null, quando.plusMinutes(10), Status.CANCELADA);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado))
                .thenReturn(Optional.of(pacienteDoLogado(pacienteId)));
        when(consultaMapper.toEntity(dto)).thenReturn(nova);
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), any(), any()))
                .thenReturn(List.of(cancelada));
        when(consultaRepository.save(nova)).thenReturn(nova);

        assertThatCode(() -> consultaService.cadastrar(dto)).doesNotThrowAnyException();

        verify(consultaRepository).save(nova);
        verify(auditoriaService).registrar(eq(logado), eq(AcaoAuditoria.CRIACAO), eq("Consulta"), any());
    }

    @Test
    @DisplayName("atualizarConsulta: a propria consulta nao conta como conflito consigo mesma")
    void atualizarIgnoraAPropriaConsultaNoConflito() {
        UUID id = UUID.randomUUID();
        Consulta alvo = Fixtures.consulta(logado, null, quando, Status.AGENDADA);
        alvo.setId(id);
        ConsultaAtualizacaoDTO dto = new ConsultaAtualizacaoDTO(quando, Status.AGENDADA);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, logado)).thenReturn(Optional.of(alvo));
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), any(), any()))
                .thenReturn(List.of(alvo));
        when(consultaRepository.save(alvo)).thenReturn(alvo);

        assertThatCode(() -> consultaService.atualizarConsulta(id, dto)).doesNotThrowAnyException();

        verify(consultaRepository).save(alvo);
        verify(auditoriaService).registrar(logado, AcaoAuditoria.ATUALIZACAO, "Consulta", id);
    }

    @Test
    @DisplayName("buscarPorId: consulta de outro usuario responde 404")
    void buscarPorIdDeOutroUsuario() {
        UUID id = UUID.randomUUID();
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, logado)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consultaService.buscarPorId(id))
                .isInstanceOf(ConsultaNaoEncontradaException.class);

        verify(consultaRepository, never()).findById(any());
    }

    @Test
    @DisplayName("auditoria: consulta registra criacao, visualizacao, atualizacao e exclusao")
    void consultaEAuditadaNosQuatroPontos() {
        UUID id = UUID.randomUUID();
        Consulta consulta = Fixtures.consulta(logado, null, quando, Status.AGENDADA);
        consulta.setId(id);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, logado)).thenReturn(Optional.of(consulta));

        consultaService.buscarPorId(id);
        verify(auditoriaService).registrar(logado, AcaoAuditoria.VISUALIZACAO, "Consulta", id);

        consultaService.deletarConsulta(id);
        verify(auditoriaService).registrar(logado, AcaoAuditoria.EXCLUSAO, "Consulta", id);
    }

    @Test
    @DisplayName("auditoria: consulta de outro psicologo nao gera log")
    void acessoNegadoNaoAudita() {
        UUID id = UUID.randomUUID();
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, logado)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consultaService.buscarPorId(id))
                .isInstanceOf(ConsultaNaoEncontradaException.class);

        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("auditoria: agenda do dia nao audita cada consulta da lista")
    void agendaNaoAuditaPorItem() {
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), any(), any()))
                .thenReturn(List.of(Fixtures.consulta(logado, null, quando, Status.AGENDADA)));

        consultaService.listarAgendaDoDia();

        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("listarAgenda: sem 'de' e 'ate' se comporta exatamente como a agenda do dia")
    void listarAgendaSemParametrosEAgendaDoDia() {
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), any(), any()))
                .thenReturn(List.of(Fixtures.consulta(logado, null, quando, Status.AGENDADA)));

        consultaService.listarAgenda(null, null);

        ArgumentCaptor<LocalDateTime> inicio = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> fim = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(consultaRepository).findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), inicio.capture(),
                fim.capture());

        assertThat(inicio.getValue()).isEqualTo(LocalDate.now().atStartOfDay());
        assertThat(fim.getValue()).isEqualTo(LocalDate.now().atTime(LocalTime.MAX));
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("listarAgenda: com 'de' e 'ate' busca do inicio do primeiro dia ao fim do ultimo")
    void listarAgendaComIntervaloBuscaDoInicioAoFimDosDias() {
        LocalDate de = LocalDate.of(2026, 9, 1);
        LocalDate ate = LocalDate.of(2026, 9, 7);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), any(), any()))
                .thenReturn(List.of());

        consultaService.listarAgenda(de, ate);

        verify(consultaRepository).findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(
                logado, de.atStartOfDay(), ate.atTime(LocalTime.MAX));
    }

    @Test
    @DisplayName("listarAgenda: so 'de' informado (sem 'ate') e erro do cliente, nao consulta o banco")
    void listarAgendaSoDeInformadoLancaPeriodoInvalido() {
        assertThatThrownBy(() -> consultaService.listarAgenda(LocalDate.of(2026, 9, 1), null))
                .isInstanceOf(PeriodoInvalidoException.class);

        verify(consultaRepository, never()).findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(any(), any(), any());
    }

    @Test
    @DisplayName("listarAgenda: so 'ate' informado (sem 'de') e erro do cliente, nao consulta o banco")
    void listarAgendaSoAteInformadoLancaPeriodoInvalido() {
        assertThatThrownBy(() -> consultaService.listarAgenda(null, LocalDate.of(2026, 9, 7)))
                .isInstanceOf(PeriodoInvalidoException.class);

        verify(consultaRepository, never()).findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(any(), any(), any());
    }

    @Test
    @DisplayName("listarAgenda: 'ate' antes de 'de' e erro do cliente, nao consulta o banco")
    void listarAgendaComAteAntesDeDeLancaPeriodoInvalido() {
        LocalDate de = LocalDate.of(2026, 9, 7);
        LocalDate ate = LocalDate.of(2026, 9, 1);

        assertThatThrownBy(() -> consultaService.listarAgenda(de, ate))
                .isInstanceOf(PeriodoInvalidoException.class);

        verify(consultaRepository, never()).findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(any(), any(), any());
    }

    @Test
    @DisplayName("listarAgenda: com intervalo tambem nao audita cada consulta da lista")
    void listarAgendaComIntervaloNaoAuditaPorItem() {
        LocalDate de = LocalDate.of(2026, 9, 1);
        LocalDate ate = LocalDate.of(2026, 9, 7);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), any(), any()))
                .thenReturn(List.of(Fixtures.consulta(logado, null, quando, Status.AGENDADA)));

        consultaService.listarAgenda(de, ate);

        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("deletarConsulta: exclusao e logica (deletedAt + save), nunca repository.delete")
    void deletarAplicaSoftDelete() {
        UUID id = UUID.randomUUID();
        Consulta consulta = Fixtures.consulta(logado, null, quando, Status.AGENDADA);
        consulta.setId(id);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, logado)).thenReturn(Optional.of(consulta));

        consultaService.deletarConsulta(id);

        assertThat(consulta.getDeletedAt()).isNotNull();
        verify(consultaRepository).save(consulta);
        verify(consultaRepository, never()).delete(any());
    }

    @Test
    @DisplayName("cadastrarSerie gera as ocorrencias semanais com o mesmo serieId")
    void cadastrarSerieGeraOcorrencias() {
        UUID pacienteId = UUID.randomUUID();
        LocalDateTime primeira = LocalDateTime.now().plusDays(7).withNano(0);
        Paciente paciente = Fixtures.paciente(logado, "Ana");
        paciente.setId(pacienteId);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado))
                .thenReturn(Optional.of(paciente));
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), any(), any()))
                .thenReturn(List.of());
        when(consultaRepository.saveAll(anyList())).thenAnswer(invocacao -> {
            List<Consulta> recebidas = invocacao.getArgument(0);
            recebidas.forEach(c -> c.setId(UUID.randomUUID()));
            return recebidas;
        });

        ConsultaRecorrenteCadastroDTO dto =
                new ConsultaRecorrenteCadastroDTO(pacienteId, primeira, 3, null);

        SerieConsultaResponseDTO resposta = consultaService.cadastrarSerie(dto);

        ArgumentCaptor<List<Consulta>> captor = ArgumentCaptor.forClass(List.class);
        verify(consultaRepository).saveAll(captor.capture());
        List<Consulta> salvas = captor.getValue();

        assertThat(salvas).hasSize(3);
        assertThat(salvas).extracting(Consulta::getDtConsulta)
                .containsExactly(primeira, primeira.plusWeeks(1), primeira.plusWeeks(2));
        assertThat(salvas).extracting(Consulta::getSerieId).containsOnly(salvas.get(0).getSerieId());
        assertThat(salvas.get(0).getSerieId()).isNotNull();
        assertThat(salvas).allSatisfy(c -> {
            assertThat(c.getUsuario()).isSameAs(logado);
            assertThat(c.getPaciente()).isSameAs(paciente);
        });
        assertThat(resposta.quantidade()).isEqualTo(3);
        assertThat(resposta.serieId()).isEqualTo(salvas.get(0).getSerieId());
    }

    @Test
    @DisplayName("cadastrarSerie audita CRIACAO uma vez por ocorrencia")
    void cadastrarSerieAuditaCadaOcorrencia() {
        UUID pacienteId = UUID.randomUUID();
        LocalDateTime primeira = LocalDateTime.now().plusDays(7).withNano(0);
        Paciente paciente = Fixtures.paciente(logado, "Ana");
        paciente.setId(pacienteId);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado))
                .thenReturn(Optional.of(paciente));
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), any(), any()))
                .thenReturn(List.of());
        when(consultaRepository.saveAll(anyList())).thenAnswer(invocacao -> {
            List<Consulta> recebidas = invocacao.getArgument(0);
            recebidas.forEach(c -> c.setId(UUID.randomUUID()));
            return recebidas;
        });

        consultaService.cadastrarSerie(new ConsultaRecorrenteCadastroDTO(pacienteId, primeira, 4, null));

        verify(auditoriaService, times(4)).registrar(eq(logado), eq(AcaoAuditoria.CRIACAO), eq("Consulta"), any());
    }

    @Test
    @DisplayName("cadastrarSerie aborta a serie inteira quando uma ocorrencia conflita")
    void cadastrarSerieAbortaEmConflito() {
        UUID pacienteId = UUID.randomUUID();
        LocalDateTime primeira = LocalDateTime.now().plusDays(7).withNano(0);
        Paciente paciente = Fixtures.paciente(logado, "Ana");
        paciente.setId(pacienteId);

        Consulta jaAgendada = Fixtures.consulta(logado, paciente, primeira.plusWeeks(2), Status.AGENDADA);
        jaAgendada.setId(UUID.randomUUID());

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado))
                .thenReturn(Optional.of(paciente));
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), any(), any()))
                .thenReturn(List.of(jaAgendada));

        ConsultaRecorrenteCadastroDTO dto =
                new ConsultaRecorrenteCadastroDTO(pacienteId, primeira, 4, null);

        assertThatThrownBy(() -> consultaService.cadastrarSerie(dto))
                .isInstanceOf(ConflitoDeHorarioException.class)
                .hasMessageContaining(primeira.plusWeeks(2).toString());

        verify(consultaRepository, never()).saveAll(anyList());
        verify(consultaRepository, never()).save(any());
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("cadastrarSerie ignora consulta CANCELADA na checagem de conflito")
    void cadastrarSerieIgnoraCancelada() {
        UUID pacienteId = UUID.randomUUID();
        LocalDateTime primeira = LocalDateTime.now().plusDays(7).withNano(0);
        Paciente paciente = Fixtures.paciente(logado, "Ana");
        paciente.setId(pacienteId);

        Consulta cancelada = Fixtures.consulta(logado, paciente, primeira, Status.CANCELADA);
        cancelada.setId(UUID.randomUUID());

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado))
                .thenReturn(Optional.of(paciente));
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), any(), any()))
                .thenReturn(List.of(cancelada));
        when(consultaRepository.saveAll(anyList())).thenAnswer(invocacao -> {
            List<Consulta> recebidas = invocacao.getArgument(0);
            recebidas.forEach(c -> c.setId(UUID.randomUUID()));
            return recebidas;
        });

        consultaService.cadastrarSerie(new ConsultaRecorrenteCadastroDTO(pacienteId, primeira, 2, null));

        verify(consultaRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("cadastrarSerie com paciente de outro psicologo responde 404 e nao grava nada")
    void cadastrarSerieComPacienteDeOutroUsuario() {
        UUID pacienteId = UUID.randomUUID();
        LocalDateTime primeira = LocalDateTime.now().plusDays(7).withNano(0);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado)).thenReturn(Optional.empty());

        ConsultaRecorrenteCadastroDTO dto =
                new ConsultaRecorrenteCadastroDTO(pacienteId, primeira, 4, null);

        assertThatThrownBy(() -> consultaService.cadastrarSerie(dto))
                .isInstanceOf(PacienteNaoEncontradoException.class);

        verify(consultaRepository, never()).saveAll(anyList());
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("cadastrarSerie por dtLimite gera ate a data limite")
    void cadastrarSeriePorDtLimite() {
        UUID pacienteId = UUID.randomUUID();
        LocalDateTime primeira = LocalDateTime.now().plusDays(7).withNano(0);
        Paciente paciente = Fixtures.paciente(logado, "Ana");
        paciente.setId(pacienteId);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado))
                .thenReturn(Optional.of(paciente));
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), any(), any()))
                .thenReturn(List.of());
        when(consultaRepository.saveAll(anyList())).thenAnswer(invocacao -> {
            List<Consulta> recebidas = invocacao.getArgument(0);
            recebidas.forEach(c -> c.setId(UUID.randomUUID()));
            return recebidas;
        });

        SerieConsultaResponseDTO resposta = consultaService.cadastrarSerie(
                new ConsultaRecorrenteCadastroDTO(pacienteId, primeira, null,
                        primeira.plusWeeks(3).toLocalDate()));

        assertThat(resposta.quantidade()).isEqualTo(4);
    }

    @Test
    @DisplayName("cancelarSerie apaga so as ocorrencias futuras, por soft delete, e audita cada uma")
    void cancelarSerieApagaSoAsFuturas() {
        UUID serieId = UUID.randomUUID();
        Paciente paciente = Fixtures.paciente(logado, "Ana");

        Consulta passada = Fixtures.consulta(logado, paciente, LocalDateTime.now().minusWeeks(1), Status.REALIZADA);
        passada.setId(UUID.randomUUID());
        passada.setSerieId(serieId);
        Consulta futura = Fixtures.consulta(logado, paciente, LocalDateTime.now().plusWeeks(1), Status.AGENDADA);
        futura.setId(UUID.randomUUID());
        futura.setSerieId(serieId);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findBySerieIdAndUsuarioAndDeletedAtIsNull(serieId, logado))
                .thenReturn(List.of(passada, futura));

        SerieCancelamentoResponseDTO resposta = consultaService.cancelarSerie(serieId);

        assertThat(resposta.ocorrenciasRemovidas()).isEqualTo(1);
        assertThat(resposta.serieId()).isEqualTo(serieId);
        assertThat(futura.getDeletedAt()).isNotNull();
        assertThat(passada.getDeletedAt()).isNull();
        verify(consultaRepository, never()).delete(any());
        verify(auditoriaService, times(1)).registrar(logado, AcaoAuditoria.EXCLUSAO, "Consulta", futura.getId());
        verify(auditoriaService, never()).registrar(any(), any(), any(), eq(passada.getId()));
    }

    @Test
    @DisplayName("cancelarSerie de serie sem ocorrencia futura devolve contagem zero, nao 404")
    void cancelarSerieSemFuturasDevolveZero() {
        UUID serieId = UUID.randomUUID();
        Paciente paciente = Fixtures.paciente(logado, "Ana");

        Consulta passada = Fixtures.consulta(logado, paciente, LocalDateTime.now().minusWeeks(1), Status.REALIZADA);
        passada.setId(UUID.randomUUID());
        passada.setSerieId(serieId);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findBySerieIdAndUsuarioAndDeletedAtIsNull(serieId, logado))
                .thenReturn(List.of(passada));

        SerieCancelamentoResponseDTO resposta = consultaService.cancelarSerie(serieId);

        assertThat(resposta.ocorrenciasRemovidas()).isZero();
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("cancelarSerie de serie inexistente ou de outro psicologo responde 404")
    void cancelarSerieInexistente() {
        UUID serieId = UUID.randomUUID();

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(consultaRepository.findBySerieIdAndUsuarioAndDeletedAtIsNull(serieId, logado)).thenReturn(List.of());

        assertThatThrownBy(() -> consultaService.cancelarSerie(serieId))
                .isInstanceOf(ConsultaNaoEncontradaException.class);

        verify(consultaRepository, never()).saveAll(anyList());
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("cadastrarSerie propaga falha da auditoria em vez de engolir, para a transacao rolar de volta")
    void cadastrarSeriePropagaFalhaDaAuditoria() {
        UUID pacienteId = UUID.randomUUID();
        LocalDateTime primeira = LocalDateTime.now().plusDays(7).withNano(0);
        Paciente paciente = Fixtures.paciente(logado, "Ana");
        paciente.setId(pacienteId);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado))
                .thenReturn(Optional.of(paciente));
        when(consultaRepository.findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(eq(logado), any(), any()))
                .thenReturn(List.of());
        when(consultaRepository.saveAll(anyList())).thenAnswer(invocacao -> {
            List<Consulta> recebidas = invocacao.getArgument(0);
            recebidas.forEach(c -> c.setId(UUID.randomUUID()));
            return recebidas;
        });

        doNothing().doNothing().doThrow(new IllegalStateException("falha na auditoria"))
                .when(auditoriaService).registrar(any(), any(), any(), any());

        ConsultaRecorrenteCadastroDTO dto = new ConsultaRecorrenteCadastroDTO(pacienteId, primeira, 4, null);

        assertThatThrownBy(() -> consultaService.cadastrarSerie(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("falha na auditoria");
    }

    @Test
    @DisplayName("cadastrarSerie rejeita horizonte degenerado vindo de chamador interno, sem tocar no banco")
    void cadastrarSerieRejeitaHorizonteDegenerado() {
        UUID pacienteId = UUID.randomUUID();
        LocalDateTime primeira = LocalDateTime.now().plusDays(7).withNano(0);
        Paciente paciente = Fixtures.paciente(logado, "Ana");
        paciente.setId(pacienteId);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado))
                .thenReturn(Optional.of(paciente));

        ConsultaRecorrenteCadastroDTO umaSessao = new ConsultaRecorrenteCadastroDTO(pacienteId, primeira, 1, null);

        assertThatThrownBy(() -> consultaService.cadastrarSerie(umaSessao))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 2 e 52");

        verify(consultaRepository, never()).findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(any(), any(), any());
        verify(consultaRepository, never()).saveAll(anyList());
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }
}
