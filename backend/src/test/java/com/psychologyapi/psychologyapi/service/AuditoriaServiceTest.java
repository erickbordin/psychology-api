package com.psychologyapi.psychologyapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.psychologyapi.psychologyapi.controller.dto.auditoria.LogAuditoriaResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.FiltroAuditoriaInvalidoException;
import com.psychologyapi.psychologyapi.mapper.LogAuditoriaMapper;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.model.entity.LogAuditoria;
import com.psychologyapi.psychologyapi.repository.LogAuditoriaRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;
import com.psychologyapi.psychologyapi.support.Fixtures;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private LogAuditoriaRepository logAuditoriaRepository;
    @Mock
    private LogAuditoriaMapper logAuditoriaMapper;
    @Mock
    private SecurityService securityService;
    @Mock
    private PacientesDaTrilha pacientesDaTrilha;

    @InjectMocks
    private AuditoriaService auditoriaService;

    private final Usuario logado = Fixtures.usuario("dono@teste.com");

    @BeforeEach
    void resolverSemPacientes() {
        lenient().when(pacientesDaTrilha.porLinha(any(), any())).thenReturn(Map.of());
    }

    private LogAuditoria log(AcaoAuditoria acao, String entidade) {
        LogAuditoria log = Fixtures.logAuditoria(logado, acao, entidade, UUID.randomUUID());
        log.setId(UUID.randomUUID());
        return log;
    }

    @Test
    @DisplayName("registrar: grava o log com o usuario, a acao e o id da entidade recebidos")
    void registrarGravaOLog() {
        UUID entidadeId = UUID.randomUUID();

        auditoriaService.registrar(logado, AcaoAuditoria.CRIACAO, "Paciente", entidadeId);

        verify(logAuditoriaRepository).save(any(LogAuditoria.class));
    }

    @Test
    @DisplayName("listar sem filtro: busca so os logs do usuario logado, nunca de um id vindo de fora")
    void listarSemFiltroUsaOUsuarioLogado() {
        Pageable pageable = PageRequest.of(0, 20);
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(logAuditoriaRepository.findByUsuarioOrderByCreatedAtDesc(logado, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        auditoriaService.listar(pageable, null, null);

        verify(logAuditoriaRepository).findByUsuarioOrderByCreatedAtDesc(logado, pageable);
    }

    @Test
    @DisplayName("listar sem filtro: repassa o Pageable recebido e mapeia a pagina para DTO")
    void listarSemFiltroRepassaOPageableEMapeia() {
        Pageable segundaPagina = PageRequest.of(1, 5);
        LogAuditoria log = log(AcaoAuditoria.VISUALIZACAO, "Paciente");
        LogAuditoriaResponseDTO dto = new LogAuditoriaResponseDTO(log.getId(), AcaoAuditoria.VISUALIZACAO,
                "Paciente", log.getEntidadeId(), LocalDateTime.now(), null, null, null);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(logAuditoriaRepository.findByUsuarioOrderByCreatedAtDesc(logado, segundaPagina))
                .thenReturn(new PageImpl<>(List.of(log)));
        when(logAuditoriaMapper.toResponseDTO(log, null)).thenReturn(dto);

        Page<LogAuditoriaResponseDTO> pagina = auditoriaService.listar(segundaPagina, null, null);

        verify(logAuditoriaRepository).findByUsuarioOrderByCreatedAtDesc(logado, segundaPagina);
        assertThat(pagina.getContent()).containsExactly(dto);
    }

    @Test
    @DisplayName("listar sem filtro: a leitura da auditoria nao gera log de auditoria dela mesma")
    void listarSemFiltroNaoSeAudita() {
        Pageable pageable = PageRequest.of(0, 20);
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(logAuditoriaRepository.findByUsuarioOrderByCreatedAtDesc(logado, pageable))
                .thenReturn(new PageImpl<>(List.of(log(AcaoAuditoria.CRIACAO, "Paciente"))));

        auditoriaService.listar(pageable, null, null);

        verify(logAuditoriaRepository, never()).save(any());
    }

    @Test
    @DisplayName("listar com entidade: busca pelo usuario logado e a entidade, repassando o Pageable")
    void listarComEntidadeFiltraPorEntidade() {
        Pageable pageable = PageRequest.of(0, 20);
        LogAuditoria log = log(AcaoAuditoria.VISUALIZACAO, "Consulta");
        LogAuditoriaResponseDTO dto = new LogAuditoriaResponseDTO(log.getId(), AcaoAuditoria.VISUALIZACAO,
                "Consulta", log.getEntidadeId(), LocalDateTime.now(), null, null, null);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(logAuditoriaRepository.findByUsuarioAndEntidadeOrderByCreatedAtDesc(logado, "Consulta", pageable))
                .thenReturn(new PageImpl<>(List.of(log)));
        when(logAuditoriaMapper.toResponseDTO(log, null)).thenReturn(dto);

        Page<LogAuditoriaResponseDTO> pagina = auditoriaService.listar(pageable, "Consulta", null);

        verify(logAuditoriaRepository).findByUsuarioAndEntidadeOrderByCreatedAtDesc(logado, "Consulta", pageable);
        verify(logAuditoriaRepository, never()).findByUsuarioOrderByCreatedAtDesc(any(), any());
        verify(logAuditoriaRepository, never())
                .findByUsuarioAndEntidadeAndEntidadeIdOrderByCreatedAtDesc(any(), any(), any(), any());
        assertThat(pagina.getContent()).containsExactly(dto);
    }

    @Test
    @DisplayName("listar com entidade e entidadeId: busca a historia de um unico registro, repassando o Pageable")
    void listarComEntidadeEEntidadeIdFiltraPeloRegistro() {
        Pageable pageable = PageRequest.of(0, 20);
        UUID entidadeId = UUID.randomUUID();
        LogAuditoria log = Fixtures.logAuditoria(logado, AcaoAuditoria.ATUALIZACAO, "Paciente", entidadeId);
        log.setId(UUID.randomUUID());
        LogAuditoriaResponseDTO dto = new LogAuditoriaResponseDTO(log.getId(), AcaoAuditoria.ATUALIZACAO,
                "Paciente", entidadeId, LocalDateTime.now(), null, null, null);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(logAuditoriaRepository.findByUsuarioAndEntidadeAndEntidadeIdOrderByCreatedAtDesc(logado, "Paciente",
                entidadeId, pageable)).thenReturn(new PageImpl<>(List.of(log)));
        when(logAuditoriaMapper.toResponseDTO(log, null)).thenReturn(dto);

        Page<LogAuditoriaResponseDTO> pagina = auditoriaService.listar(pageable, "Paciente", entidadeId);

        verify(logAuditoriaRepository).findByUsuarioAndEntidadeAndEntidadeIdOrderByCreatedAtDesc(logado, "Paciente",
                entidadeId, pageable);
        verify(logAuditoriaRepository, never()).findByUsuarioOrderByCreatedAtDesc(any(), any());
        verify(logAuditoriaRepository, never()).findByUsuarioAndEntidadeOrderByCreatedAtDesc(any(), any(), any());
        assertThat(pagina.getContent()).containsExactly(dto);
    }

    @Test
    @DisplayName("listar com entidadeId sem entidade: rejeita antes de tocar o usuario logado ou o repositorio")
    void listarComEntidadeIdSemEntidadeRejeita() {
        Pageable pageable = PageRequest.of(0, 20);
        UUID entidadeId = UUID.randomUUID();

        assertThatThrownBy(() -> auditoriaService.listar(pageable, null, entidadeId))
                .isInstanceOf(FiltroAuditoriaInvalidoException.class);

        verifyNoInteractions(securityService);
        verifyNoInteractions(logAuditoriaRepository);
    }
}
