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

import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.PostgresContainerConfig;

/**
 * Valida contra Postgres real que as derived queries de fato filtram por dono e
 * por deletedAt. Mock nao cobre isso: o nome do metodo e que vira o SQL.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class PacienteRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Test
    @DisplayName("busca por id nao enxerga paciente de outro psicologo")
    void naoRetornaPacienteDeOutroUsuario() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Usuario intruso = em.persistAndFlush(Fixtures.usuario("intruso@teste.com"));
        Paciente paciente = em.persistAndFlush(Fixtures.paciente(dono, "Ana"));

        assertThat(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(paciente.getId(), intruso)).isEmpty();
        assertThat(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(paciente.getId(), dono)).isPresent();
    }

    @Test
    @DisplayName("busca por id nao enxerga paciente com exclusao logica")
    void naoRetornaPacienteSoftDeletado() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Paciente paciente = em.persistAndFlush(Fixtures.paciente(dono, "Ana"));

        paciente.setDeletedAt(LocalDateTime.now());
        em.flush();

        assertThat(pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(paciente.getId(), dono)).isEmpty();
    }

    @Test
    @DisplayName("listagem por nome filtra pelo dono, ignora case e ignora excluido")
    void listagemFiltraPorDonoNomeESoftDelete() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Usuario intruso = em.persistAndFlush(Fixtures.usuario("intruso@teste.com"));

        em.persistAndFlush(Fixtures.paciente(dono, "Ana"));
        em.persistAndFlush(Fixtures.paciente(dono, "Bruno"));
        em.persistAndFlush(Fixtures.paciente(intruso, "Ariel"));

        Paciente excluido = em.persistAndFlush(Fixtures.paciente(dono, "Anderson"));
        excluido.setDeletedAt(LocalDateTime.now());
        em.flush();

        List<Paciente> encontrados = pacienteRepository
                .findByUsuarioAndNomePacienteContainingIgnoreCaseAndDeletedAtIsNull(dono, "an");

        assertThat(encontrados).extracting(Paciente::getNomePaciente).containsExactly("Ana");
    }

    @Test
    @DisplayName("listagem sem filtro de nome traz todos os pacientes do dono, ignora outro psicologo e excluido")
    void listagemPorUsuarioFiltraDonoESoftDelete() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Usuario intruso = em.persistAndFlush(Fixtures.usuario("intruso@teste.com"));

        em.persistAndFlush(Fixtures.paciente(dono, "Ana"));
        em.persistAndFlush(Fixtures.paciente(dono, "Bruno"));
        em.persistAndFlush(Fixtures.paciente(intruso, "Carla"));

        Paciente excluido = em.persistAndFlush(Fixtures.paciente(dono, "Excluido"));
        excluido.setDeletedAt(LocalDateTime.now());
        em.flush();

        List<Paciente> encontrados = pacienteRepository.findByUsuarioAndDeletedAtIsNull(dono);

        assertThat(encontrados).extracting(Paciente::getNomePaciente).containsExactlyInAnyOrder("Ana", "Bruno");
    }
}
