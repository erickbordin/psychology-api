package com.psychologyapi.psychologyapi.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "consulta", schema = "public")
@Data
public class Consulta {

    @Id
    @Column(name = "id_consulta", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @Column(name = "data_hora_consulta", nullable = false)
    private LocalDateTime dtConsulta;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_consulta", nullable = false)
    private Status status;

    @Column(name = "valor_consulta", nullable = true, precision = 10, scale = 2)
    private BigDecimal valor;

    /** Data da baixa. Nulo so significa "a receber" quando o status e
     *  REALIZADA — falta nao e cobrada (spec 5.3). Quem somar dinheiro
     *  filtrando so por este campo conta falta como divida. */
    @Column(name = "pago_em_consulta", nullable = true)
    private LocalDateTime pagoEm;

    @Column(name = "created_at_consulta", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at_consulta")
    private LocalDateTime deletedAt;

    @Column(name = "id_serie")
    private UUID serieId;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
        if(this.status == null){
            this.status = Status.AGENDADA;
        }
    }

}
