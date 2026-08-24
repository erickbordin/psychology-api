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

import com.psychologyapi.psychologyapi.model.Lembrete;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.PostgresContainerConfig;

/**
 * Lembrete nao tem Usuario direto: o dono e alcancado via Paciente, entao o
 * isolamento depende da navegacao aninhada Paciente_Usuario no nome do metodo.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class LembreteRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private LembreteRepository lembreteRepository;

    @Test
    @DisplayName("busca por id nao enxerga lembrete de paciente de outro psicologo")
    void naoRetornaLembreteDePacienteDeOutroUsuario() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Usuario intruso = em.persistAndFlush(Fixtures.usuario("intruso@teste.com"));
        Paciente paciente = em.persistAndFlush(Fixtures.paciente(dono, "Ana"));

        Lembrete lembrete = em.persistAndFlush(Fixtures.lembrete(paciente, "ligar para a paciente"));

        assertThat(lembreteRepository.findByIdAndPaciente_UsuarioAndDeletedAtIsNull(lembrete.getId(), dono))
                .isPresent();
        assertThat(lembreteRepository.findByIdAndPaciente_UsuarioAndDeletedAtIsNull(lembrete.getId(), intruso))
                .isEmpty();
    }

    @Test
    @DisplayName("listagem ignora lembrete excluido e ordena do mais novo para o mais antigo")
    void listagemIgnoraSoftDeleteEOrdenaDesc() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Paciente paciente = em.persistAndFlush(Fixtures.paciente(dono, "Ana"));

        Lembrete antigo = em.persistAndFlush(Fixtures.lembrete(paciente, "antigo"));
        Lembrete recente = em.persistAndFlush(Fixtures.lembrete(paciente, "recente"));
        Lembrete excluido = em.persistAndFlush(Fixtures.lembrete(paciente, "excluido"));

        antigo.setCreatedAt(LocalDateTime.now().minusDays(2));
        recente.setCreatedAt(LocalDateTime.now());
        excluido.setCreatedAt(LocalDateTime.now().minusDays(1));
        excluido.setDeletedAt(LocalDateTime.now());
        em.flush();

        List<Lembrete> lembretes = lembreteRepository
                .findByPacienteAndPaciente_UsuarioAndDeletedAtIsNullOrderByCreatedAtDesc(paciente, dono);

        assertThat(lembretes).extracting(Lembrete::getDescricao).containsExactly("recente", "antigo");
    }

    @Test
    @DisplayName("lembrete nasce nao concluido e nao excluido")
    void defaultsDoPrePersist() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Paciente paciente = em.persistAndFlush(Fixtures.paciente(dono, "Ana"));

        Lembrete lembrete = em.persistAndFlush(Fixtures.lembrete(paciente, "ligar"));

        assertThat(lembrete.getConcluido()).isFalse();
        assertThat(lembrete.getDeletedAt()).isNull();
    }
}
