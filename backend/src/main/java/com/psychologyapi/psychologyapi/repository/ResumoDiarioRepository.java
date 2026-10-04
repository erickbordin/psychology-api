package com.psychologyapi.psychologyapi.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.psychologyapi.psychologyapi.model.EstadoAviso;
import com.psychologyapi.psychologyapi.model.ResumoDiario;

public interface ResumoDiarioRepository extends JpaRepository<ResumoDiario, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO resumo_diario
                (id_resumo, id_usuario, data_resumo, estado_resumo, tentativas_resumo,
                 proxima_tentativa_resumo, created_at_resumo)
            VALUES (:id, :usuarioId, :data, 'PENDENTE', 0, :agora, :agora)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int registrarSeNovo(@Param("id") UUID id, @Param("usuarioId") UUID usuarioId,
            @Param("data") LocalDate data, @Param("agora") LocalDateTime agora);

    @Query("SELECT r.id FROM ResumoDiario r WHERE r.estado = :estado AND r.proximaTentativa <= :agora")
    List<UUID> idsPendentesAte(@Param("estado") EstadoAviso estado, @Param("agora") LocalDateTime agora);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ResumoDiario r SET r.tentativas = r.tentativas + 1
            WHERE r.id = :id AND r.tentativas = :lidas
              AND r.estado = com.psychologyapi.psychologyapi.model.EstadoAviso.PENDENTE
            """)
    int reivindicarTentativa(@Param("id") UUID id, @Param("lidas") int lidas);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ResumoDiario r SET r.estado = :estado, r.enviadoEm = :enviadoEm, r.erro = :erro, r.proximaTentativa = :proxima WHERE r.id = :id")
    int fechar(@Param("id") UUID id, @Param("estado") EstadoAviso estado,
            @Param("enviadoEm") LocalDateTime enviadoEm, @Param("erro") String erro,
            @Param("proxima") LocalDateTime proxima);
}
