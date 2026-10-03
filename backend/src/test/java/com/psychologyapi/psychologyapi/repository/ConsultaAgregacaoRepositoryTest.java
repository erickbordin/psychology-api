package com.psychologyapi.psychologyapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import com.psychologyapi.psychologyapi.repository.projecao.TotaisFinanceirosDoMes;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.PostgresContainerConfig;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class ConsultaAgregacaoRepositoryTest {

    private static final LocalDateTime INICIO = LocalDateTime.parse("2026-12-01T00:00:00");
    private static final LocalDateTime FIM = LocalDateTime.parse("2027-01-01T00:00:00");

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ConsultaRepository consultaRepository;

    private Consulta salvar(Usuario dono, Paciente paciente, String quando, Status status, String valor, String pagoEm) {
        Consulta consulta = Fixtures.consulta(dono, paciente, LocalDateTime.parse(quando), status);
        consulta.setValor(valor == null ? null : new BigDecimal(valor));
        consulta.setPagoEm(pagoEm == null ? null : LocalDateTime.parse(pagoEm));
        return em.persistAndFlush(consulta);
    }

    @Test
    @DisplayName("totais por competencia: so REALIZADA, sem exclusao logica, so o mes pedido")
    void totaisPorCompetencia() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Paciente paciente = em.persistAndFlush(Fixtures.paciente(dono, "Ana"));

        salvar(dono, paciente, "2026-12-03T10:00:00", Status.REALIZADA, "180.00", "2027-01-10T09:00:00");
        salvar(dono, paciente, "2026-12-04T10:00:00", Status.REALIZADA, "180.00", null);
        salvar(dono, paciente, "2026-12-05T10:00:00", Status.REALIZADA, null, null);
        salvar(dono, paciente, "2026-12-06T10:00:00", Status.FALTOU, "180.00", null);
        salvar(dono, paciente, "2026-12-07T10:00:00", Status.CANCELADA, "180.00", null);
        Consulta excluida = salvar(dono, paciente, "2026-12-08T10:00:00", Status.REALIZADA, "180.00", null);
        excluida.setDeletedAt(LocalDateTime.parse("2026-12-09T10:00:00"));
        em.flush();
        salvar(dono, paciente, "2026-11-20T10:00:00", Status.REALIZADA, "150.00", null);

        TotaisFinanceirosDoMes totais = consultaRepository.totaisFinanceirosDoMes(dono, INICIO, FIM);

        assertThat(totais.getFaturado()).isEqualByComparingTo("360.00");
        assertThat(totais.getRecebido()).isEqualByComparingTo("180.00");
        assertThat(totais.getSemValor()).isEqualTo(1L);
    }

    @Test
    @DisplayName("borda do mes: 31/12 23:30 conta, 01/01 00:00 nao conta")
    void bordaDoMes() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Paciente paciente = em.persistAndFlush(Fixtures.paciente(dono, "Ana"));

        salvar(dono, paciente, "2026-12-31T23:30:00", Status.REALIZADA, "100.00", null);
        salvar(dono, paciente, "2027-01-01T00:00:00", Status.REALIZADA, "100.00", null);

        TotaisFinanceirosDoMes totais = consultaRepository.totaisFinanceirosDoMes(dono, INICIO, FIM);

        assertThat(totais.getFaturado()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("mes vazio: faturado e recebido nulos, semValor zero")
    void mesVazio() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));

        TotaisFinanceirosDoMes totais = consultaRepository.totaisFinanceirosDoMes(dono, INICIO, FIM);

        assertThat(totais.getFaturado()).isNull();
        assertThat(totais.getRecebido()).isNull();
        assertThat(totais.getSemValor()).isEqualTo(0L);
    }

    @Test
    @DisplayName("totais nao vazam consulta de outro psicologo")
    void totaisRespeitamIsolamento() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Usuario outro = em.persistAndFlush(Fixtures.usuario("outro@teste.com"));
        Paciente doOutro = em.persistAndFlush(Fixtures.paciente(outro, "Beto"));

        salvar(outro, doOutro, "2026-12-03T10:00:00", Status.REALIZADA, "500.00", null);

        TotaisFinanceirosDoMes totais = consultaRepository.totaisFinanceirosDoMes(dono, INICIO, FIM);

        assertThat(totais.getFaturado()).isNull();
        assertThat(totais.getSemValor()).isEqualTo(0L);
    }

    @Test
    @DisplayName("sessoes do mes: so REALIZADAS do intervalo, em ordem crescente, com o paciente carregado")
    void sessoesDoMes() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Paciente paciente = em.persistAndFlush(Fixtures.paciente(dono, "Ana"));

        Consulta tarde = salvar(dono, paciente, "2026-12-10T10:00:00", Status.REALIZADA, "180.00", null);
        Consulta cedo = salvar(dono, paciente, "2026-12-02T10:00:00", Status.REALIZADA, null, null);
        salvar(dono, paciente, "2026-12-05T10:00:00", Status.FALTOU, "180.00", null);
        salvar(dono, paciente, "2026-11-30T10:00:00", Status.REALIZADA, "180.00", null);
        salvar(dono, paciente, "2027-01-01T00:00:00", Status.REALIZADA, "180.00", null);
        em.clear();

        List<Consulta> sessoes = consultaRepository
                .findByUsuarioAndStatusAndDtConsultaGreaterThanEqualAndDtConsultaLessThanAndDeletedAtIsNullOrderByDtConsultaAsc(
                        dono, Status.REALIZADA, INICIO, FIM);

        assertThat(sessoes).extracting(Consulta::getId).containsExactly(cedo.getId(), tarde.getId());
        assertThat(sessoes.get(0).getPaciente().getNomePaciente()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("em aberto: REALIZADA com valor e sem baixa de qualquer mes, inclusive de paciente excluido")
    void emAberto() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Paciente paciente = em.persistAndFlush(Fixtures.paciente(dono, "Ana"));
        Paciente excluido = em.persistAndFlush(Fixtures.paciente(dono, "Clara"));
        excluido.setDeletedAt(LocalDateTime.parse("2026-12-20T10:00:00"));
        em.flush();

        Consulta novembro = salvar(dono, paciente, "2026-11-10T10:00:00", Status.REALIZADA, "150.00", null);
        Consulta dezembro = salvar(dono, paciente, "2026-12-10T10:00:00", Status.REALIZADA, "180.00", null);
        Consulta doExcluido = salvar(dono, excluido, "2026-12-11T10:00:00", Status.REALIZADA, "200.00", null);
        salvar(dono, paciente, "2026-12-12T10:00:00", Status.REALIZADA, "180.00", "2026-12-13T10:00:00");
        salvar(dono, paciente, "2026-12-14T10:00:00", Status.REALIZADA, null, null);
        salvar(dono, paciente, "2026-12-15T10:00:00", Status.FALTOU, "180.00", null);
        Consulta apagada = salvar(dono, paciente, "2026-12-16T10:00:00", Status.REALIZADA, "180.00", null);
        apagada.setDeletedAt(LocalDateTime.parse("2026-12-17T10:00:00"));
        em.flush();
        em.clear();

        List<Consulta> abertas = consultaRepository
                .findByUsuarioAndStatusAndValorIsNotNullAndPagoEmIsNullAndDeletedAtIsNullOrderByDtConsultaAsc(
                        dono, Status.REALIZADA);

        assertThat(abertas).extracting(Consulta::getId)
                .containsExactly(novembro.getId(), dezembro.getId(), doExcluido.getId());
        assertThat(abertas.get(2).getPaciente().getDeletedAt()).isNotNull();
    }
}
