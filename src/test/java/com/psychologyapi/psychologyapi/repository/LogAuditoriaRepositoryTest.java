package com.psychologyapi.psychologyapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.model.entity.LogAuditoria;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.PostgresContainerConfig;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class LogAuditoriaRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private LogAuditoriaRepository logAuditoriaRepository;

    /**
     * O createdAt nasce do @PrePersist, entao e sobrescrito depois do insert para
     * que a ordenacao do teste nao dependa do relogio entre dois inserts seguidos.
     */
    private LogAuditoria logEm(Usuario dono, String entidade, UUID entidadeId, LocalDateTime createdAt) {
        LogAuditoria log = em.persistAndFlush(
                Fixtures.logAuditoria(dono, AcaoAuditoria.VISUALIZACAO, entidade, entidadeId));
        log.setCreatedAt(createdAt);
        em.flush();
        return log;
    }

    private LogAuditoria logEm(Usuario dono, String entidade, LocalDateTime createdAt) {
        return logEm(dono, entidade, UUID.randomUUID(), createdAt);
    }

    @Test
    @DisplayName("listagem nao enxerga log de auditoria de outro psicologo")
    void naoRetornaLogDeOutroUsuario() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Usuario intruso = em.persistAndFlush(Fixtures.usuario("intruso@teste.com"));

        LogAuditoria doDono = logEm(dono, "Paciente", LocalDateTime.now());
        em.clear();

        assertThat(logAuditoriaRepository.findByUsuarioOrderByCreatedAtDesc(dono, PageRequest.of(0, 20)))
                .extracting(LogAuditoria::getId)
                .containsExactly(doDono.getId());
        assertThat(logAuditoriaRepository.findByUsuarioOrderByCreatedAtDesc(intruso, PageRequest.of(0, 20)))
                .isEmpty();
    }

    @Test
    @DisplayName("listagem devolve o log mais recente primeiro")
    void ordenaPeloMaisRecente() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));

        LogAuditoria antigo = logEm(dono, "Paciente", LocalDateTime.now().minusDays(2));
        LogAuditoria recente = logEm(dono, "Consulta", LocalDateTime.now());
        em.clear();

        Page<LogAuditoria> pagina = logAuditoriaRepository.findByUsuarioOrderByCreatedAtDesc(dono,
                PageRequest.of(0, 20));

        assertThat(pagina.getContent())
                .extracting(LogAuditoria::getId)
                .containsExactly(recente.getId(), antigo.getId());
    }

    @Test
    @DisplayName("listagem respeita o tamanho da pagina e conta o total")
    void respeitaAPaginacao() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));

        logEm(dono, "Paciente", LocalDateTime.now().minusDays(3));
        logEm(dono, "Consulta", LocalDateTime.now().minusDays(2));
        LogAuditoria recente = logEm(dono, "Anotacao", LocalDateTime.now());
        em.clear();

        Page<LogAuditoria> primeiraPagina = logAuditoriaRepository.findByUsuarioOrderByCreatedAtDesc(dono,
                PageRequest.of(0, 2));

        assertThat(primeiraPagina.getContent()).hasSize(2);
        assertThat(primeiraPagina.getTotalElements()).isEqualTo(3);
        assertThat(primeiraPagina.getContent().get(0).getId()).isEqualTo(recente.getId());
    }

    @Test
    @DisplayName("filtro por entidade: devolve so as linhas do tipo pedido, mais recente primeiro, e nunca de outro psicologo")
    void filtraPorEntidade() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Usuario intruso = em.persistAndFlush(Fixtures.usuario("intruso@teste.com"));

        LogAuditoria pacienteAntigo = logEm(dono, "Paciente", LocalDateTime.now().minusDays(2));
        LogAuditoria pacienteRecente = logEm(dono, "Paciente", LocalDateTime.now());
        logEm(dono, "Consulta", LocalDateTime.now());
        logEm(intruso, "Paciente", LocalDateTime.now());
        em.clear();

        Page<LogAuditoria> pagina = logAuditoriaRepository.findByUsuarioAndEntidadeOrderByCreatedAtDesc(dono,
                "Paciente", PageRequest.of(0, 20));

        assertThat(pagina.getContent())
                .extracting(LogAuditoria::getId)
                .containsExactly(pacienteRecente.getId(), pacienteAntigo.getId());
    }

    @Test
    @DisplayName("filtro por entidade: respeita a paginacao")
    void filtraPorEntidadeRespeitaAPaginacao() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));

        logEm(dono, "Paciente", LocalDateTime.now().minusDays(3));
        logEm(dono, "Paciente", LocalDateTime.now().minusDays(2));
        LogAuditoria recente = logEm(dono, "Paciente", LocalDateTime.now());
        logEm(dono, "Consulta", LocalDateTime.now());
        em.clear();

        Page<LogAuditoria> primeiraPagina = logAuditoriaRepository.findByUsuarioAndEntidadeOrderByCreatedAtDesc(dono,
                "Paciente", PageRequest.of(0, 2));

        assertThat(primeiraPagina.getContent()).hasSize(2);
        assertThat(primeiraPagina.getTotalElements()).isEqualTo(3);
        assertThat(primeiraPagina.getContent().get(0).getId()).isEqualTo(recente.getId());
    }

    @Test
    @DisplayName("filtro por entidade e entidadeId: devolve so a historia daquele registro, nem de outro tipo nem de outro psicologo")
    void filtraPorEntidadeEEntidadeId() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Usuario intruso = em.persistAndFlush(Fixtures.usuario("intruso@teste.com"));
        UUID idDoPaciente = UUID.randomUUID();

        LogAuditoria criacao = logEm(dono, "Paciente", idDoPaciente, LocalDateTime.now().minusDays(1));
        LogAuditoria atualizacao = logEm(dono, "Paciente", idDoPaciente, LocalDateTime.now());
        // mesmo UUID, mas de outra entidade: nao pode vazar para a busca do paciente
        logEm(dono, "Consulta", idDoPaciente, LocalDateTime.now());
        // mesmo registro, mas de outro psicologo: nao pode vazar
        logEm(intruso, "Paciente", idDoPaciente, LocalDateTime.now());
        // outro paciente do mesmo dono: nao pode entrar na historia deste registro
        logEm(dono, "Paciente", UUID.randomUUID(), LocalDateTime.now());
        em.clear();

        Page<LogAuditoria> pagina = logAuditoriaRepository
                .findByUsuarioAndEntidadeAndEntidadeIdOrderByCreatedAtDesc(dono, "Paciente", idDoPaciente,
                        PageRequest.of(0, 20));

        assertThat(pagina.getContent())
                .extracting(LogAuditoria::getId)
                .containsExactly(atualizacao.getId(), criacao.getId());
    }

    @Test
    @DisplayName("filtro por entidade e entidadeId: respeita a paginacao")
    void filtraPorEntidadeEEntidadeIdRespeitaAPaginacao() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        UUID idDoPaciente = UUID.randomUUID();

        logEm(dono, "Paciente", idDoPaciente, LocalDateTime.now().minusDays(2));
        logEm(dono, "Paciente", idDoPaciente, LocalDateTime.now().minusDays(1));
        LogAuditoria recente = logEm(dono, "Paciente", idDoPaciente, LocalDateTime.now());
        em.clear();

        Page<LogAuditoria> primeiraPagina = logAuditoriaRepository
                .findByUsuarioAndEntidadeAndEntidadeIdOrderByCreatedAtDesc(dono, "Paciente", idDoPaciente,
                        PageRequest.of(0, 2));

        assertThat(primeiraPagina.getContent()).hasSize(2);
        assertThat(primeiraPagina.getTotalElements()).isEqualTo(3);
        assertThat(primeiraPagina.getContent().get(0).getId()).isEqualTo(recente.getId());
    }
}
