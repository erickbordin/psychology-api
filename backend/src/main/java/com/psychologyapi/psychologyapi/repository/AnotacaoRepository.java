package com.psychologyapi.psychologyapi.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.psychologyapi.psychologyapi.model.Anotacao;

public interface AnotacaoRepository extends JpaRepository<Anotacao, UUID>{

    Optional<Anotacao> findByIdAndUsuarioAndDeletedAtIsNull(UUID id, Usuario usuario);

    @EntityGraph(attributePaths = "paciente")
    List<Anotacao> findByIdInAndUsuario(Collection<UUID> ids, Usuario usuario);

    Page<Anotacao> findByPacienteAndUsuarioAndDeletedAtIsNullOrderByCreatedAtDesc(Paciente paciente, Usuario usuario, Pageable pageable);

    /** As anotacoes do paciente que estao presas a alguma sessao — as avulsas
     *  ficam de fora, porque nao entram na linha do tempo (spec 5.1). Uma
     *  consulta so para todas elas: o alternativo seria uma por sessao, que e
     *  N+1 numa tela que abre a cada atendimento. */
    List<Anotacao> findByPacienteAndUsuarioAndConsultaIsNotNullAndDeletedAtIsNullOrderByCreatedAtAsc(
            Paciente paciente, Usuario usuario);

    /** Ha alguma anotacao VIVA presa a esta sessao? Guarda de
     *  {@code ConsultaService.deletarConsulta} (decisao do dono, revisao da
     *  Fatia 2): excluir a sessao nao apaga a anotacao, mas a tira da ficha —
     *  conteudo clinico invisivel no prontuario, sem aviso.
     *
     *  Nao filtra por usuario de proposito: quem chama ja resolveu a consulta
     *  por {@code findByIdAndUsuarioAndDeletedAtIsNull}, entao o isolamento ja
     *  foi provado uma vez, e filtrar de novo aqui esconderia a anotacao de
     *  outro psicologo presa a esta sessao — um estado que nao deveria existir,
     *  mas que, se existir, tem de impedir a exclusao, nao passar batido. */
    boolean existsByConsultaAndDeletedAtIsNull(Consulta consulta);

    /** Os ids das consultas, entre as informadas, que ja tem anotacao. Uma
     *  consulta so para o dia inteiro, em vez de uma por sessao.
     *
     *  Filtra por usuario porque isolamento por psicologo e invariante: a
     *  anotacao de outro psicologo nunca pode decidir se esta sessao esta
     *  pendente. Quem chama nao deve passar lista vazia — `IN ()` e SQL
     *  invalido no Postgres. */
    @Query("""
            SELECT DISTINCT a.consulta.id FROM Anotacao a
            WHERE a.usuario = :usuario
              AND a.consulta.id IN :consultaIds
              AND a.deletedAt IS NULL
            """)
    List<UUID> idsDeConsultasComAnotacao(
            @Param("usuario") Usuario usuario,
            @Param("consultaIds") List<UUID> consultaIds);

}
