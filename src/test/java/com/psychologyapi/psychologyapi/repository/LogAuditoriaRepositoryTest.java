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
    private LogAuditoria logEm(Usuario dono, String entidade, LocalDateTime createdAt) {
        LogAuditoria log = em.persistAndFlush(
                Fixtures.logAuditoria(dono, AcaoAuditoria.VISUALIZACAO, entidade, UUID.randomUUID()));
        log.setCreatedAt(createdAt);
        em.flush();
        return log;
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
}
