package com.psychologyapi.psychologyapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import com.psychologyapi.psychologyapi.model.Pendencia;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.PostgresContainerConfig;

/**
 * Pendencia nao tem Usuario direto: o dono e alcancado via Paciente, entao o
 * isolamento depende da navegacao aninhada Paciente_Usuario no nome do metodo.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class PendenciaRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PendenciaRepository pendenciaRepository;

    @Test
    @DisplayName("busca por id nao enxerga pendencia de paciente de outro psicologo")
    void naoRetornaPendenciaDePacienteDeOutroUsuario() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Usuario intruso = em.persistAndFlush(Fixtures.usuario("intruso@teste.com"));
        Paciente paciente = em.persistAndFlush(Fixtures.paciente(dono, "Ana"));

        Pendencia pendencia = em.persistAndFlush(Fixtures.pendencia(paciente, "ligar para a paciente"));

        assertThat(pendenciaRepository.findByIdAndPaciente_UsuarioAndDeletedAtIsNull(pendencia.getId(), dono))
                .isPresent();
        assertThat(pendenciaRepository.findByIdAndPaciente_UsuarioAndDeletedAtIsNull(pendencia.getId(), intruso))
                .isEmpty();
    }

    @Test
    @DisplayName("listagem ignora pendencia excluida e ordena do mais novo para o mais antigo")
    void listagemIgnoraSoftDeleteEOrdenaDesc() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Paciente paciente = em.persistAndFlush(Fixtures.paciente(dono, "Ana"));

        Pendencia antigo = em.persistAndFlush(Fixtures.pendencia(paciente, "antigo"));
        Pendencia recente = em.persistAndFlush(Fixtures.pendencia(paciente, "recente"));
        Pendencia excluido = em.persistAndFlush(Fixtures.pendencia(paciente, "excluido"));

        antigo.setCreatedAt(LocalDateTime.now().minusDays(2));
        recente.setCreatedAt(LocalDateTime.now());
        excluido.setCreatedAt(LocalDateTime.now().minusDays(1));
        excluido.setDeletedAt(LocalDateTime.now());
        em.flush();

        List<Pendencia> pendencias = pendenciaRepository
                .findByPacienteAndPaciente_UsuarioAndDeletedAtIsNullOrderByCreatedAtDesc(paciente, dono,
                        PageRequest.of(0, 20))
                .getContent();

        assertThat(pendencias).extracting(Pendencia::getDescricao).containsExactly("recente", "antigo");
    }

    @Test
    @DisplayName("pendencia nasce nao concluida e nao excluida")
    void defaultsDoPrePersist() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Paciente paciente = em.persistAndFlush(Fixtures.paciente(dono, "Ana"));

        Pendencia pendencia = em.persistAndFlush(Fixtures.pendencia(paciente, "ligar"));

        assertThat(pendencia.getConcluido()).isFalse();
        assertThat(pendencia.getDeletedAt()).isNull();
    }
}
