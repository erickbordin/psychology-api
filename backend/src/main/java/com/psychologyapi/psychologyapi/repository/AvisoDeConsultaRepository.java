package com.psychologyapi.psychologyapi.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.psychologyapi.psychologyapi.model.AvisoDeConsulta;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Destinatario;
import com.psychologyapi.psychologyapi.model.EstadoAviso;

public interface AvisoDeConsultaRepository extends JpaRepository<AvisoDeConsulta, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO aviso_de_consulta
                (id_aviso, id_consulta, destinatario_aviso, canal_aviso, agendado_para_aviso,
                 estado_aviso, data_hora_consulta_aviso, tentativas_aviso, proxima_tentativa_aviso)
            VALUES (:id, :consultaId, 'PACIENTE', 'EMAIL', :agendadoPara,
                    'PENDENTE', :dataHoraConsulta, 0, :agora)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int registrarSeNovo(@Param("id") UUID id, @Param("consultaId") UUID consultaId,
            @Param("agendadoPara") LocalDateTime agendadoPara,
            @Param("dataHoraConsulta") LocalDateTime dataHoraConsulta,
            @Param("agora") LocalDateTime agora);

    List<AvisoDeConsulta> findByConsultaInAndDestinatario(List<Consulta> consultas, Destinatario destinatario);

    @Query("SELECT a.id FROM AvisoDeConsulta a WHERE a.estado = :estado AND a.proximaTentativa <= :agora")
    List<UUID> idsPendentesAte(@Param("estado") EstadoAviso estado, @Param("agora") LocalDateTime agora);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AvisoDeConsulta a SET a.tentativas = a.tentativas + 1
            WHERE a.id = :id AND a.tentativas = :lidas
              AND a.estado = com.psychologyapi.psychologyapi.model.EstadoAviso.PENDENTE
            """)
    int reivindicarTentativa(@Param("id") UUID id, @Param("lidas") int lidas);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AvisoDeConsulta a SET a.estado = :estado, a.enviadoEm = :enviadoEm, a.erro = :erro, a.proximaTentativa = :proxima WHERE a.id = :id")
    int fechar(@Param("id") UUID id, @Param("estado") EstadoAviso estado,
            @Param("enviadoEm") LocalDateTime enviadoEm, @Param("erro") String erro,
            @Param("proxima") LocalDateTime proxima);
}
