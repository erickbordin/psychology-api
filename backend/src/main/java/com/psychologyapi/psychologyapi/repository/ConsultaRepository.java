package com.psychologyapi.psychologyapi.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;

public interface ConsultaRepository extends JpaRepository<Consulta, UUID>{

    List<Consulta> findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(Usuario usuario, LocalDateTime inicio, LocalDateTime fim);
    Optional<Consulta> findByIdAndUsuarioAndDeletedAtIsNull(UUID id, Usuario usuario);
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

    /** Sessao que passou da hora e continua AGENDADA — a unica pendencia da
     *  Fatia 1 (spec 4 (1) e 9). As outras duas chegam nas Fatias 2 e 3. */
    List<Consulta> findByUsuarioAndStatusAndDtConsultaBeforeAndDeletedAtIsNullOrderByDtConsultaAsc(
            Usuario usuario, Status status, LocalDateTime limite);

}
