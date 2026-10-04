package com.psychologyapi.psychologyapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.psychologyapi.psychologyapi.authsecurity.CustomAuthentication;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaRecorrenteCadastroDTO;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.PostgresContainerConfig;

/**
 * A unica classe de teste de contexto completo que NAO usa a anotacao composta
 * support/HttpTest, e de proposito: o HttpTest e @Transactional, e uma transacao
 * de teste ao redor do metodo faria o @Transactional do service apenas
 * participar dela — o caminho de commit nunca rodaria e o rollback seria o da
 * propria transacao do teste, provando nada.
 *
 * O preco de nao ser transacional e que esta classe grava de verdade e precisa
 * limpar o que criou no @AfterEach, para nao poluir o container compartilhado.
 *
 * O que esta sendo provado: com a auditoria falhando no meio da serie, nenhuma
 * Consulta sobrevive. Se o @Transactional cair do cadastrarSerie, o saveAll do
 * Spring Data commita por conta propria e as linhas ficam — este teste quebra.
 */
@SpringBootTest(properties = {
        "JWT_SECRET=c2VncmVkb2RldGVzdGVjb21wcmlkb29zdWZpY2llbnRlcGFyYWhtYWNzaGEyNTY=",
        "app.avisos.habilitado=false"
})
@Import(PostgresContainerConfig.class)
class SerieAtomicidadeTest {

    @Autowired
    private ConsultaService consultaService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PacienteRepository pacienteRepository;
    @Autowired
    private ConsultaRepository consultaRepository;

    @MockitoSpyBean
    private AuditoriaService auditoriaService;

    private Usuario dono;
    private Paciente paciente;

    @BeforeEach
    void prepararUsuarioLogado() {
        dono = usuarioRepository.save(Fixtures.usuario("atomicidade@teste.com"));
        paciente = pacienteRepository.save(Fixtures.paciente(dono, "Ana"));
        SecurityContextHolder.getContext().setAuthentication(new CustomAuthentication(dono));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
        consultaRepository.deleteAll(consultaRepository.findAll().stream()
                .filter(consulta -> consulta.getUsuario().getId().equals(dono.getId()))
                .toList());
        pacienteRepository.delete(paciente);
        usuarioRepository.delete(dono);
    }

    @Test
    @DisplayName("falha da auditoria no meio da serie desfaz as consultas ja gravadas")
    void falhaDaAuditoriaDesfazASerieInteira() {
        long antes = consultaRepository.count();

        doNothing().doNothing().doThrow(new IllegalStateException("falha simulada na auditoria"))
                .when(auditoriaService).registrar(any(), any(), any(), any());

        ConsultaRecorrenteCadastroDTO dto = new ConsultaRecorrenteCadastroDTO(
                paciente.getId(), LocalDateTime.now().plusDays(7).withNano(0), 4, null);

        assertThatThrownBy(() -> consultaService.cadastrarSerie(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("falha simulada na auditoria");

        assertThat(consultaRepository.count())
                .as("nenhuma consulta da serie pode sobreviver a falha no meio da gravacao")
                .isEqualTo(antes);
    }
}
