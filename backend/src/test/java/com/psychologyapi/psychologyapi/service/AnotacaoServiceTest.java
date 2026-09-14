package com.psychologyapi.psychologyapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

import com.psychologyapi.psychologyapi.controller.dto.anotacao.AnotacaoCadastroDTO;
import com.psychologyapi.psychologyapi.exceptions.AnotacaoNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.mapper.AnotacaoMapper;
import com.psychologyapi.psychologyapi.model.Anotacao;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.AnotacaoRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;
import com.psychologyapi.psychologyapi.support.Fixtures;

@ExtendWith(MockitoExtension.class)
class AnotacaoServiceTest {

    @Mock
    private AnotacaoRepository anotacaoRepository;
    @Mock
    private AnotacaoMapper anotacaoMapper;
    @Mock
    private SecurityService securityService;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private AnotacaoService anotacaoService;

    private final Usuario logado = Fixtures.usuario("dono@teste.com");

    private Paciente pacienteDoLogado(UUID pacienteId) {
        Paciente paciente = Fixtures.paciente(logado, "Ana");
        paciente.setId(pacienteId);
        return paciente;
    }

    @Test
    @DisplayName("cadastrar: paciente de outro usuario responde 404 e nao grava anotacao")
    void cadastrarComPacienteDeOutroUsuario() {
        UUID pacienteId = UUID.randomUUID();
        AnotacaoCadastroDTO dto = new AnotacaoCadastroDTO("sessao 1", null);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> anotacaoService.cadastrar(pacienteId, dto))
                .isInstanceOf(PacienteNaoEncontradoException.class);

        verify(anotacaoRepository, never()).save(any());
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("cadastrar: registra auditoria de CRIACAO com o id da anotacao salva")
    void cadastrarRegistraAuditoria() {
        UUID pacienteId = UUID.randomUUID();
        UUID anotacaoId = UUID.randomUUID();
        AnotacaoCadastroDTO dto = new AnotacaoCadastroDTO("sessao 1", null);

        Anotacao nova = Fixtures.anotacao(null, null, "sessao 1");
        Anotacao salva = Fixtures.anotacao(logado, pacienteDoLogado(pacienteId), "sessao 1");
        salva.setId(anotacaoId);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado))
                .thenReturn(Optional.of(pacienteDoLogado(pacienteId)));
        when(anotacaoMapper.toEntity(dto)).thenReturn(nova);
        when(anotacaoRepository.save(nova)).thenReturn(salva);

        anotacaoService.cadastrar(pacienteId, dto);

        assertThat(nova.getUsuario()).isSameAs(logado);
        verify(auditoriaService).registrar(logado, AcaoAuditoria.CRIACAO, "Anotacao", anotacaoId);
    }

    @Test
    @DisplayName("listarPorPaciente: audita o paciente visitado uma vez, nao cada anotacao da lista")
    void listarAuditaOPacienteUmaVez() {
        UUID pacienteId = UUID.randomUUID();
        Paciente paciente = pacienteDoLogado(pacienteId);

        Anotacao a1 = Fixtures.anotacao(logado, paciente, "a1");
        a1.setId(UUID.randomUUID());
        Anotacao a2 = Fixtures.anotacao(logado, paciente, "a2");
        a2.setId(UUID.randomUUID());

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado))
                .thenReturn(Optional.of(paciente));
        Pageable pageable = PageRequest.of(0, 20);
        when(anotacaoRepository.findByPacienteAndUsuarioAndDeletedAtIsNullOrderByCreatedAtDesc(paciente, logado,
                pageable)).thenReturn(new PageImpl<>(List.of(a1, a2)));

        anotacaoService.listarPorPaciente(pacienteId, pageable);

        verify(auditoriaService, times(1)).registrar(logado, AcaoAuditoria.VISUALIZACAO, "Paciente", pacienteId);
        verify(auditoriaService, never()).registrar(any(), any(), eq("Anotacao"), any());
    }

    @Test
    @DisplayName("listarPorPaciente: repassa o Pageable recebido para o repository")
    void listarRepassaOPageable() {
        UUID pacienteId = UUID.randomUUID();
        Paciente paciente = pacienteDoLogado(pacienteId);
        Pageable segundaPagina = PageRequest.of(1, 5);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, logado))
                .thenReturn(Optional.of(paciente));
        when(anotacaoRepository.findByPacienteAndUsuarioAndDeletedAtIsNullOrderByCreatedAtDesc(paciente, logado,
                segundaPagina)).thenReturn(new PageImpl<>(List.of()));

        anotacaoService.listarPorPaciente(pacienteId, segundaPagina);

        verify(anotacaoRepository).findByPacienteAndUsuarioAndDeletedAtIsNullOrderByCreatedAtDesc(paciente, logado,
                segundaPagina);
    }

    @Test
    @DisplayName("deletarAnotacao: audita depois do save, nunca antes")
    void deletarAuditaDepoisDoSave() {
        UUID anotacaoId = UUID.randomUUID();
        Anotacao anotacao = Fixtures.anotacao(logado, null, "sessao 1");
        anotacao.setId(anotacaoId);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(anotacaoRepository.findByIdAndUsuarioAndDeletedAtIsNull(anotacaoId, logado))
                .thenReturn(Optional.of(anotacao));

        anotacaoService.deletarAnotacao(anotacaoId);

        InOrder ordem = inOrder(anotacaoRepository, auditoriaService);
        ordem.verify(anotacaoRepository).save(anotacao);
        ordem.verify(auditoriaService).registrar(logado, AcaoAuditoria.EXCLUSAO, "Anotacao", anotacaoId);
    }

    @Test
    @DisplayName("deletarAnotacao: exclusao e logica (deletedAt + save) e auditada")
    void deletarAplicaSoftDelete() {
        UUID anotacaoId = UUID.randomUUID();
        Anotacao anotacao = Fixtures.anotacao(logado, null, "sessao 1");
        anotacao.setId(anotacaoId);

        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(anotacaoRepository.findByIdAndUsuarioAndDeletedAtIsNull(anotacaoId, logado))
                .thenReturn(Optional.of(anotacao));

        anotacaoService.deletarAnotacao(anotacaoId);

        assertThat(anotacao.getDeletedAt()).isNotNull();
        verify(anotacaoRepository).save(anotacao);
        verify(anotacaoRepository, never()).delete(any());
        verify(auditoriaService).registrar(logado, AcaoAuditoria.EXCLUSAO, "Anotacao", anotacaoId);
    }

    @Test
    @DisplayName("deletarAnotacao: anotacao de outro usuario responde 404")
    void deletarDeOutroUsuario() {
        UUID anotacaoId = UUID.randomUUID();
        when(securityService.obterUsuarioLogado()).thenReturn(logado);
        when(anotacaoRepository.findByIdAndUsuarioAndDeletedAtIsNull(anotacaoId, logado)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> anotacaoService.deletarAnotacao(anotacaoId))
                .isInstanceOf(AnotacaoNaoEncontradaException.class);

        verify(anotacaoRepository, never()).save(any());
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }
}
