package com.psychologyapi.psychologyapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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

import com.psychologyapi.psychologyapi.controller.dto.paciente.PacienteAtualizacaoDTO;
import com.psychologyapi.psychologyapi.controller.dto.paciente.PacienteCadastroDTO;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.mapper.PacienteMapper;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;
import com.psychologyapi.psychologyapi.support.Fixtures;

@ExtendWith(MockitoExtension.class)
class PacienteServiceTest {

    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private PacienteMapper pacienteMapper;
    @Mock
    private SecurityService securityService;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private PacienteService pacienteService;

    private final Usuario logado = Fixtures.usuario("dono@teste.com");

    @Test
    @DisplayName("cadastrar: usuario vem do contexto de seguranca, nunca do DTO")
    void cadastrarUsaUsuarioLogado() {
        PacienteCadastroDTO dto = new PacienteCadastroDTO("Ana", null, null, LocalDate.of(1990, 1, 1));
        Paciente novo = new Paciente();

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteMapper.toEntity(dto)).thenReturn(novo);
        when(pacienteRepository.save(novo)).thenReturn(novo);

        pacienteService.cadastrar(dto);

        ArgumentCaptor<Paciente> capturado = ArgumentCaptor.forClass(Paciente.class);
        verify(pacienteRepository).save(capturado.capture());
        assertThat(capturado.getValue().getUsuario()).isSameAs(logado);
    }

    @Test
    @DisplayName("cadastrar: registra auditoria de CRIACAO com o id do paciente salvo")
    void cadastrarRegistraAuditoria() {
        UUID id = UUID.randomUUID();
        PacienteCadastroDTO dto = new PacienteCadastroDTO("Ana", null, null, LocalDate.of(1990, 1, 1));
        Paciente novo = new Paciente();
        Paciente salvo = Fixtures.paciente(logado, "Ana");
        salvo.setId(id);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteMapper.toEntity(dto)).thenReturn(novo);
        when(pacienteRepository.save(novo)).thenReturn(salvo);

        pacienteService.cadastrar(dto);

        verify(auditoriaService).registrar(logado, AcaoAuditoria.CRIACAO, "Paciente", id);
    }

    @Test
    @DisplayName("buscarPorId: registra auditoria de VISUALIZACAO do paciente acessado")
    void buscarPorIdRegistraAuditoria() {
        UUID id = UUID.randomUUID();
        Paciente paciente = Fixtures.paciente(logado, "Ana");
        paciente.setId(id);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, logado)).thenReturn(Optional.of(paciente));

        pacienteService.buscarPorId(id);

        verify(auditoriaService).registrar(logado, AcaoAuditoria.VISUALIZACAO, "Paciente", id);
    }

    @Test
    @DisplayName("listarPacientes: nao audita cada paciente da lista")
    void listarNaoAuditaCadaPaciente() {
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByUsuarioAndNomePacienteContainingIgnoreCaseAndDeletedAtIsNull(logado, "an"))
                .thenReturn(List.of(Fixtures.paciente(logado, "Ana")));

        pacienteService.listarPacientes("an");

        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("buscarPorId: paciente de outro usuario responde 404, nunca 403")
    void buscarPorIdDeOutroUsuarioLancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, logado)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pacienteService.buscarPorId(id))
                .isInstanceOf(PacienteNaoEncontradoException.class);

        verify(pacienteRepository, never()).findById(any());
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("listarPacientes: filtra sempre pelo usuario logado")
    void listarFiltraPeloUsuarioLogado() {
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByUsuarioAndNomePacienteContainingIgnoreCaseAndDeletedAtIsNull(logado, "an"))
                .thenReturn(List.of());

        pacienteService.listarPacientes("an");

        verify(pacienteRepository).findByUsuarioAndNomePacienteContainingIgnoreCaseAndDeletedAtIsNull(logado, "an");
        verify(pacienteRepository, never()).findAll();
    }

    @Test
    @DisplayName("atualizarPaciente: paciente de outro usuario responde 404 e nao persiste nada")
    void atualizarDeOutroUsuarioLancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        PacienteAtualizacaoDTO dto = new PacienteAtualizacaoDTO("Novo nome", null, null, null);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, logado)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pacienteService.atualizarPaciente(id, dto))
                .isInstanceOf(PacienteNaoEncontradoException.class);

        verify(pacienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("deletarPaciente: exclusao e logica (deletedAt + save), nunca repository.delete")
    void deletarAplicaSoftDelete() {
        UUID id = UUID.randomUUID();
        Paciente paciente = Fixtures.paciente(logado, "Ana");
        paciente.setId(id);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, logado)).thenReturn(Optional.of(paciente));

        pacienteService.deletarPaciente(id);

        assertThat(paciente.getDeletedAt()).isNotNull();
        verify(pacienteRepository).save(paciente);
        verify(pacienteRepository, never()).delete(any());
        verify(auditoriaService).registrar(logado, AcaoAuditoria.EXCLUSAO, "Paciente", id);
    }

    @Test
    @DisplayName("deletarPaciente: paciente de outro usuario responde 404")
    void deletarDeOutroUsuarioLancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, logado)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pacienteService.deletarPaciente(id))
                .isInstanceOf(PacienteNaoEncontradoException.class);

        verify(pacienteRepository, never()).delete(any());
        verify(pacienteRepository, never()).save(any());
    }
}
