package com.psychologyapi.psychologyapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

    @InjectMocks
    private AuditoriaService auditoriaService;

    private final Usuario logado = Fixtures.usuario("dono@teste.com");

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
    @DisplayName("listar: busca so os logs do usuario logado, nunca de um id vindo de fora")
    void listarUsaOUsuarioLogado() {
        Pageable pageable = PageRequest.of(0, 20);
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(logAuditoriaRepository.findByUsuarioOrderByCreatedAtDesc(logado, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        auditoriaService.listar(pageable);

        verify(logAuditoriaRepository).findByUsuarioOrderByCreatedAtDesc(logado, pageable);
    }

    @Test
    @DisplayName("listar: repassa o Pageable recebido e mapeia a pagina para DTO")
    void listarRepassaOPageableEMapeia() {
        Pageable segundaPagina = PageRequest.of(1, 5);
        LogAuditoria log = log(AcaoAuditoria.VISUALIZACAO, "Paciente");
        LogAuditoriaResponseDTO dto = new LogAuditoriaResponseDTO(log.getId(), AcaoAuditoria.VISUALIZACAO,
                "Paciente", log.getEntidadeId(), LocalDateTime.now());

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(logAuditoriaRepository.findByUsuarioOrderByCreatedAtDesc(logado, segundaPagina))
                .thenReturn(new PageImpl<>(List.of(log)));
        when(logAuditoriaMapper.toResponseDTO(log)).thenReturn(dto);

        Page<LogAuditoriaResponseDTO> pagina = auditoriaService.listar(segundaPagina);

        verify(logAuditoriaRepository).findByUsuarioOrderByCreatedAtDesc(logado, segundaPagina);
        assertThat(pagina.getContent()).containsExactly(dto);
    }

    @Test
    @DisplayName("listar: a leitura da auditoria nao gera log de auditoria dela mesma")
    void listarNaoSeAudita() {
        Pageable pageable = PageRequest.of(0, 20);
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(logAuditoriaRepository.findByUsuarioOrderByCreatedAtDesc(logado, pageable))
                .thenReturn(new PageImpl<>(List.of(log(AcaoAuditoria.CRIACAO, "Paciente"))));

        auditoriaService.listar(pageable);

        verify(logAuditoriaRepository, never()).save(any());
    }
}
