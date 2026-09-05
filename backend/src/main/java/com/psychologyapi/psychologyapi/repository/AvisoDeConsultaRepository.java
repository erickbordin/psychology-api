package com.psychologyapi.psychologyapi.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.psychologyapi.psychologyapi.model.AvisoDeConsulta;

public interface AvisoDeConsultaRepository extends JpaRepository<AvisoDeConsulta, UUID> {

    /**
     * So traz aviso de consulta que ainda existe e nao foi cancelada — sem
     * isso, cancelar ou excluir a consulta deixa o aviso PENDENTE e o
     * paciente recebe lembrete de uma sessao que nao vai acontecer mais
     * (I2 do laudo da revisao do conjunto, 2026-09-05).
     */
    @Query("""
           SELECT a FROM AvisoDeConsulta a
           WHERE a.estado = com.psychologyapi.psychologyapi.model.EstadoAviso.PENDENTE
             AND a.agendadoPara <= :agora
             AND a.consulta.deletedAt IS NULL
             AND a.consulta.status <> com.psychologyapi.psychologyapi.model.Status.CANCELADA
           """)
    List<AvisoDeConsulta> aVencer(@Param("agora") LocalDateTime agora);

    /**
     * Reivindica o aviso antes de enviar. Devolve 1 se este processo ganhou a
     * linha, 0 se outro ja tinha ganhado.
     *
     * Com duas instancias no ar, as duas varrem e o paciente recebe dois
     * e-mails (spec 5.7). O UPDATE condicional resolve: so envia quem afetou
     * uma linha. Barato agora, caro de descobrir depois.
     *
     * <p><b>ARMADILHA para quem chama isto na mesma transacao que buscou o
     * aviso via {@link #aVencer(LocalDateTime)}:</b> este e um UPDATE em
     * massa (bulk update) — ele muda a LINHA no banco, mas NAO atualiza o
     * objeto {@code AvisoDeConsulta} ja carregado no contexto de persistencia.
     * A entidade gerenciada continua em memoria com {@code estado = PENDENTE}
     * e {@code enviadoEm = null} mesmo depois da chamada ter devolvido 1.
     *
     * Se, na mesma transacao, algum codigo posterior der {@code save()} (ou
     * so deixar o dirty checking do Hibernate agir no flush) nessa MESMA
     * entidade gerenciada — por exemplo para gravar {@code erro}/FALHOU num
     * envio que falhou pela metade, ou um save defensivo apos o envio — o
     * Hibernate reescreve a linha com os valores antigos que ainda estao no
     * objeto, revertendo {@code ENVIADO} para {@code PENDENTE} EM SILENCIO.
     * Isso reabre exatamente a janela de envio duplo que esta trava existe
     * para fechar: o paciente recebe dois e-mails.
     *
     * Antes de salvar a entidade correspondente depois de chamar
     * {@code reivindicar}, recarregue-a do banco (ou use apenas updates
     * pontuais/queries, nunca o objeto antigo).
     */
    @Modifying
    @Query("""
           UPDATE AvisoDeConsulta a
              SET a.estado = com.psychologyapi.psychologyapi.model.EstadoAviso.ENVIADO,
                  a.enviadoEm = :agora
            WHERE a.id = :id
              AND a.estado = com.psychologyapi.psychologyapi.model.EstadoAviso.PENDENTE
           """)
    int reivindicar(@Param("id") UUID id, @Param("agora") LocalDateTime agora);
}
