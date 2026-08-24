package com.psychologyapi.psychologyapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.PostgresContainerConfig;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class ConsultaRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ConsultaRepository consultaRepository;

    private static final LocalDateTime INICIO_DO_DIA = LocalDate.now().atStartOfDay();
    private static final LocalDateTime FIM_DO_DIA = LocalDate.now().atTime(LocalTime.MAX);

    @Test
    @DisplayName("agenda do dia nao vaza consulta de outro psicologo")
    void agendaNaoVazaConsultaDeOutroUsuario() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Usuario intruso = em.persistAndFlush(Fixtures.usuario("intruso@teste.com"));
        Paciente pacienteDoDono = em.persistAndFlush(Fixtures.paciente(dono, "Ana"));

        em.persistAndFlush(Fixtures.consulta(dono, pacienteDoDono, LocalDateTime.now(), Status.AGENDADA));

        List<Consulta> agendaDoIntruso = consultaRepository
                .findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(intruso, INICIO_DO_DIA, FIM_DO_DIA);

        assertThat(agendaDoIntruso).isEmpty();
    }

    @Test
    @DisplayName("agenda do dia ignora consulta com exclusao logica (janela de conflito nao pode considerar)")
    void agendaIgnoraConsultaSoftDeletada() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Paciente paciente = em.persistAndFlush(Fixtures.paciente(dono, "Ana"));

        Consulta ativa = em.persistAndFlush(Fixtures.consulta(dono, paciente, LocalDateTime.now(), Status.AGENDADA));
        Consulta excluida = em.persistAndFlush(Fixtures.consulta(dono, paciente, LocalDateTime.now(), Status.AGENDADA));
        excluida.setDeletedAt(LocalDateTime.now());
        em.flush();

        List<Consulta> agenda = consultaRepository
                .findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(dono, INICIO_DO_DIA, FIM_DO_DIA);

        assertThat(agenda).extracting(Consulta::getId).containsExactly(ativa.getId());
    }

    @Test
    @DisplayName("busca por id nao enxerga consulta excluida nem de outro psicologo")
    void buscaPorIdRespeitaDonoESoftDelete() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Usuario intruso = em.persistAndFlush(Fixtures.usuario("intruso@teste.com"));
        Paciente paciente = em.persistAndFlush(Fixtures.paciente(dono, "Ana"));

        Consulta consulta = em.persistAndFlush(Fixtures.consulta(dono, paciente, LocalDateTime.now(), Status.AGENDADA));

        assertThat(consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(consulta.getId(), dono)).isPresent();
        assertThat(consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(consulta.getId(), intruso)).isEmpty();

        consulta.setDeletedAt(LocalDateTime.now());
        em.flush();

        assertThat(consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(consulta.getId(), dono)).isEmpty();
    }
}
