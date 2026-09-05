package com.psychologyapi.psychologyapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.psychologyapi.psychologyapi.controller.dto.pendencia.PendenciaCadastroDTO;
import com.psychologyapi.psychologyapi.exceptions.PendenciaNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.mapper.PendenciaMapper;
import com.psychologyapi.psychologyapi.model.Pendencia;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.PendenciaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;
import com.psychologyapi.psychologyapi.support.Fixtures;

@ExtendWith(MockitoExtension.class)
class PendenciaServiceTest {

    @Mock
    private PendenciaRepository pendenciaRepository;
    @Mock
    private PendenciaMapper pendenciaMapper;
    @Mock
    private SecurityService securityService;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private PendenciaService pendenciaService;

    private final Usuario logado = Fixtures.usuario("dono@teste.com");

    private Paciente pacienteDoLogado(UUID pacienteId) {
        Paciente paciente = Fixtures.paciente(logado, "Ana");
        paciente.setId(pacienteId);
        return paciente;
    }

    @Test
    @DisplayName("cadastrar: paciente de outro usuario responde 404 e nao grava pendencia")
    void cadastrarComPacienteDeOutroUsuario() {
        UUID pacienteId = UUID.randomUUID();
        PendenciaCadastroDTO dto = new PendenciaCadastroDTO("ligar para a paciente");

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pendenciaMapper.toEntity(dto)).thenReturn(Fixtures.pendencia(null, "ligar para a paciente"));
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pendenciaService.cadastrar(pacienteId, dto))
                .isInstanceOf(PacienteNaoEncontradoException.class);

        verify(pendenciaRepository, never()).save(any());
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("listarPorPaciente: dono e checado pela navegacao aninhada Paciente -> Usuario")
    void listarUsaNavegacaoAninhada() {
        UUID pacienteId = UUID.randomUUID();
        Paciente paciente = pacienteDoLogado(pacienteId);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado))
                .thenReturn(Optional.of(paciente));
        Pageable pageable = PageRequest.of(0, 20);
        when(pendenciaRepository.findByPacienteAndPaciente_UsuarioAndDeletedAtIsNullOrderByCreatedAtDesc(paciente,
                logado, pageable)).thenReturn(new PageImpl<>(List.of()));

        pendenciaService.listarPorPaciente(pacienteId, pageable);

        verify(pendenciaRepository)
                .findByPacienteAndPaciente_UsuarioAndDeletedAtIsNullOrderByCreatedAtDesc(paciente, logado, pageable);
        verify(pendenciaRepository, never()).findAll();
    }

    @Test
    @DisplayName("concluir: seta concluido e persiste com save")
    void concluirPersiste() {
        UUID pendenciaId = UUID.randomUUID();
        Pendencia pendencia = Fixtures.pendencia(pacienteDoLogado(UUID.randomUUID()), "ligar");
        pendencia.setId(pendenciaId);
        pendencia.setConcluido(false);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pendenciaRepository.findByIdAndPaciente_UsuarioAndDeletedAtIsNull(pendenciaId, logado))
                .thenReturn(Optional.of(pendencia));
        when(pendenciaRepository.save(pendencia)).thenReturn(pendencia);

        pendenciaService.concluir(pendenciaId);

        assertThat(pendencia.getConcluido()).isTrue();
        verify(pendenciaRepository).save(pendencia);
        verify(auditoriaService).registrar(logado, AcaoAuditoria.ATUALIZACAO, "Pendencia", pendenciaId);
    }

    @Test
    @DisplayName("concluir: pendencia de paciente de outro usuario responde 404")
    void concluirDeOutroUsuario() {
        UUID pendenciaId = UUID.randomUUID();
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pendenciaRepository.findByIdAndPaciente_UsuarioAndDeletedAtIsNull(pendenciaId, logado))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> pendenciaService.concluir(pendenciaId))
                .isInstanceOf(PendenciaNaoEncontradaException.class);

        verify(pendenciaRepository, never()).save(any());
    }

    @Test
    @DisplayName("deletarPendencia: audita depois do save, nunca antes")
    void deletarAuditaDepoisDoSave() {
        UUID pendenciaId = UUID.randomUUID();
        Pendencia pendencia = Fixtures.pendencia(null, "ligar para a Ana");
        pendencia.setId(pendenciaId);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pendenciaRepository.findByIdAndPaciente_UsuarioAndDeletedAtIsNull(pendenciaId, logado))
                .thenReturn(Optional.of(pendencia));

        pendenciaService.deletarPendencia(pendenciaId);

        InOrder ordem = inOrder(pendenciaRepository, auditoriaService);
        ordem.verify(pendenciaRepository).save(pendencia);
        ordem.verify(auditoriaService).registrar(logado, AcaoAuditoria.EXCLUSAO, "Pendencia", pendenciaId);
    }

    @Test
    @DisplayName("deletarPendencia: exclusao e logica (deletedAt + save), nunca repository.delete")
    void deletarAplicaSoftDelete() {
        UUID pendenciaId = UUID.randomUUID();
        Pendencia pendencia = Fixtures.pendencia(pacienteDoLogado(UUID.randomUUID()), "ligar");
        pendencia.setId(pendenciaId);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pendenciaRepository.findByIdAndPaciente_UsuarioAndDeletedAtIsNull(pendenciaId, logado))
                .thenReturn(Optional.of(pendencia));

        pendenciaService.deletarPendencia(pendenciaId);

        assertThat(pendencia.getDeletedAt()).isNotNull();
        verify(pendenciaRepository).save(pendencia);
        verify(pendenciaRepository, never()).delete(any());
        verify(auditoriaService).registrar(logado, AcaoAuditoria.EXCLUSAO, "Pendencia", pendenciaId);
    }
}
