package com.psychologyapi.psychologyapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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
import com.psychologyapi.psychologyapi.exceptions.ConflitoDeHorarioException;
import com.psychologyapi.psychologyapi.exceptions.ConsultaNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.mapper.ConsultaMapper;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
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
}
