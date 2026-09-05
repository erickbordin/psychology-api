package com.psychologyapi.psychologyapi.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

/** Pendencia clinica do paciente — sem data, sem envio (spec 5.2). Nao
 *  confundir com AvisoDeConsulta, que e mensagem agendada presa a uma
 *  Consulta. Uma entidade so, tentando ser as duas, foi a raiz do
 *  "lembrete nao lembra nada". */
@Entity
@Table(name = "pendencia", schema = "public")
@Data
public class Pendencia {

    @Id
    @Column(name = "id_pendencia", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @Column(name = "descricao", nullable = false)
    private String descricao;

    @Column(name = "created_at_pendencia", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "concluido", nullable = false)
    private Boolean concluido;

    @Column(name = "deleted_at_pendencia")
    private LocalDateTime deletedAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.concluido = false;
    }

}
