package com.psychologyapi.psychologyapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.psychologyapi.psychologyapi.controller.dto.lembrete.LembreteCadastroDTO;
import com.psychologyapi.psychologyapi.exceptions.LembreteNaoEncontradoException;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.mapper.LembreteMapper;
import com.psychologyapi.psychologyapi.model.Lembrete;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.LembreteRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;
import com.psychologyapi.psychologyapi.support.Fixtures;

@ExtendWith(MockitoExtension.class)
class LembreteServiceTest {

    @Mock
    private LembreteRepository lembreteRepository;
    @Mock
    private LembreteMapper lembreteMapper;
    @Mock
    private SecurityService securityService;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private LembreteService lembreteService;

    private final Usuario logado = Fixtures.usuario("dono@teste.com");

    private Paciente pacienteDoLogado(UUID pacienteId) {
        Paciente paciente = Fixtures.paciente(logado, "Ana");
        paciente.setId(pacienteId);
        return paciente;
    }

    @Test
    @DisplayName("cadastrar: paciente de outro usuario responde 404 e nao grava lembrete")
    void cadastrarComPacienteDeOutroUsuario() {
        UUID pacienteId = UUID.randomUUID();
        LembreteCadastroDTO dto = new LembreteCadastroDTO("ligar para a paciente");

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(lembreteMapper.toEntity(dto)).thenReturn(Fixtures.lembrete(null, "ligar para a paciente"));
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lembreteService.cadastrar(pacienteId, dto))
                .isInstanceOf(PacienteNaoEncontradoException.class);

        verify(lembreteRepository, never()).save(any());
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
        when(lembreteRepository.findByPacienteAndPaciente_UsuarioAndDeletedAtIsNullOrderByCreatedAtDesc(paciente,
                logado, pageable)).thenReturn(new PageImpl<>(List.of()));

        lembreteService.listarPorPaciente(pacienteId, pageable);

        verify(lembreteRepository)
                .findByPacienteAndPaciente_UsuarioAndDeletedAtIsNullOrderByCreatedAtDesc(paciente, logado, pageable);
        verify(lembreteRepository, never()).findAll();
    }

    @Test
    @DisplayName("concluir: seta concluido e persiste com save")
    void concluirPersiste() {
        UUID lembreteId = UUID.randomUUID();
        Lembrete lembrete = Fixtures.lembrete(pacienteDoLogado(UUID.randomUUID()), "ligar");
        lembrete.setId(lembreteId);
        lembrete.setConcluido(false);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(lembreteRepository.findByIdAndPaciente_UsuarioAndDeletedAtIsNull(lembreteId, logado))
                .thenReturn(Optional.of(lembrete));
        when(lembreteRepository.save(lembrete)).thenReturn(lembrete);

        lembreteService.concluir(lembreteId);

        assertThat(lembrete.getConcluido()).isTrue();
        verify(lembreteRepository).save(lembrete);
        verify(auditoriaService).registrar(logado, AcaoAuditoria.ATUALIZACAO, "Lembrete", lembreteId);
    }

    @Test
    @DisplayName("concluir: lembrete de paciente de outro usuario responde 404")
    void concluirDeOutroUsuario() {
        UUID lembreteId = UUID.randomUUID();
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(lembreteRepository.findByIdAndPaciente_UsuarioAndDeletedAtIsNull(lembreteId, logado))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> lembreteService.concluir(lembreteId))
                .isInstanceOf(LembreteNaoEncontradoException.class);

        verify(lembreteRepository, never()).save(any());
    }

    @Test
    @DisplayName("deletarLembrete: exclusao e logica (deletedAt + save), nunca repository.delete")
    void deletarAplicaSoftDelete() {
        UUID lembreteId = UUID.randomUUID();
        Lembrete lembrete = Fixtures.lembrete(pacienteDoLogado(UUID.randomUUID()), "ligar");
        lembrete.setId(lembreteId);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(lembreteRepository.findByIdAndPaciente_UsuarioAndDeletedAtIsNull(lembreteId, logado))
                .thenReturn(Optional.of(lembrete));

        lembreteService.deletarLembrete(lembreteId);

        assertThat(lembrete.getDeletedAt()).isNotNull();
        verify(lembreteRepository).save(lembrete);
        verify(lembreteRepository, never()).delete(any());
        verify(auditoriaService).registrar(logado, AcaoAuditoria.EXCLUSAO, "Lembrete", lembreteId);
    }
}
