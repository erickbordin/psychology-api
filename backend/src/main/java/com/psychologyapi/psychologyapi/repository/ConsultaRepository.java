package com.psychologyapi.psychologyapi.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.projecao.ContagemDoMes;
import com.psychologyapi.psychologyapi.repository.projecao.TotaisFinanceirosDoMes;

public interface ConsultaRepository extends JpaRepository<Consulta, UUID>{

    List<Consulta> findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(Usuario usuario, LocalDateTime inicio, LocalDateTime fim);
    Optional<Consulta> findByIdAndUsuarioAndDeletedAtIsNull(UUID id, Usuario usuario);
    List<Consulta> findByIdInAndUsuarioAndDeletedAtIsNull(Collection<UUID> ids, Usuario usuario);
    List<Consulta> findBySerieIdAndUsuarioAndDeletedAtIsNull(UUID serieId, Usuario usuario);
    Page<Consulta> findByPacienteAndUsuarioAndDeletedAtIsNullOrderByDtConsultaDesc(
            Paciente paciente, Usuario usuario, Pageable pageable);

    // usado por PacienteService.deletarPaciente para cancelar as sessoes
    // ainda nao realizadas (spec 5.10) — sem paginacao, e uso interno de
    // servico, nao endpoint de listagem
    List<Consulta> findByPacienteAndUsuarioAndDeletedAtIsNull(Paciente paciente, Usuario usuario);

    // sessoes do dia para o painel Hoje (spec 5.6), ordenadas pelo banco —
    // a tela mostra o dia em ordem, e ordenar em memoria seria trabalho a mais
    List<Consulta> findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNullOrderByDtConsultaAsc(
            Usuario usuario, LocalDateTime inicio, LocalDateTime fim);

    /** Sessao que passou da hora e continua AGENDADA. */
    List<Consulta> findByUsuarioAndStatusAndDtConsultaBeforeAndDeletedAtIsNullOrderByDtConsultaAsc(
            Usuario usuario, Status status, LocalDateTime limite);

    @Query("""
            SELECT SUM(c.valor) AS faturado,
                   SUM(CASE WHEN c.pagoEm IS NOT NULL THEN c.valor END) AS recebido,
                   COUNT(CASE WHEN c.valor IS NULL THEN 1 END) AS semValor
            FROM Consulta c
            WHERE c.usuario = :usuario
              AND c.status = com.psychologyapi.psychologyapi.model.Status.REALIZADA
              AND c.deletedAt IS NULL
              AND c.dtConsulta >= :inicio
              AND c.dtConsulta < :fimExclusivo
            """)
    TotaisFinanceirosDoMes totaisFinanceirosDoMes(
            @Param("usuario") Usuario usuario,
            @Param("inicio") LocalDateTime inicio,
            @Param("fimExclusivo") LocalDateTime fimExclusivo);

    @EntityGraph(attributePaths = "paciente")
    List<Consulta> findByUsuarioAndStatusAndDtConsultaGreaterThanEqualAndDtConsultaLessThanAndDeletedAtIsNullOrderByDtConsultaAsc(
            Usuario usuario, Status status, LocalDateTime inicio, LocalDateTime fimExclusivo);

    @EntityGraph(attributePaths = "paciente")
    List<Consulta> findByUsuarioAndStatusAndValorIsNotNullAndPagoEmIsNullAndDeletedAtIsNullOrderByDtConsultaAsc(
            Usuario usuario, Status status);

    @Query("""
            SELECT COUNT(CASE WHEN c.status = com.psychologyapi.psychologyapi.model.Status.REALIZADA THEN 1 END) AS realizadas,
                   COUNT(CASE WHEN c.status = com.psychologyapi.psychologyapi.model.Status.FALTOU THEN 1 END) AS faltas,
                   COUNT(CASE WHEN c.status = com.psychologyapi.psychologyapi.model.Status.CANCELADA THEN 1 END) AS canceladas,
                   COUNT(DISTINCT CASE WHEN c.status = com.psychologyapi.psychologyapi.model.Status.REALIZADA
                                       THEN c.paciente.id END) AS pacientesAtivos
            FROM Consulta c
            WHERE c.usuario = :usuario
              AND c.deletedAt IS NULL
              AND c.dtConsulta >= :inicio
              AND c.dtConsulta < :fimExclusivo
            """)
    ContagemDoMes contagemDoMes(
            @Param("usuario") Usuario usuario,
            @Param("inicio") LocalDateTime inicio,
            @Param("fimExclusivo") LocalDateTime fimExclusivo);

    @EntityGraph(attributePaths = {"paciente", "usuario"})
    List<Consulta> findByStatusAndDtConsultaGreaterThanAndDtConsultaLessThanEqualAndDeletedAtIsNull(
            Status status, LocalDateTime depoisDe, LocalDateTime ateInclusive);

    @Query("""
            SELECT DISTINCT c.usuario FROM Consulta c
            WHERE c.status = com.psychologyapi.psychologyapi.model.Status.AGENDADA
              AND c.deletedAt IS NULL
              AND c.dtConsulta >= :inicio
              AND c.dtConsulta < :fimExclusivo
              AND c.usuario.resumoDiario = true
            """)
    List<Usuario> usuariosComResumoEntre(
            @Param("inicio") LocalDateTime inicio,
            @Param("fimExclusivo") LocalDateTime fimExclusivo);
}
