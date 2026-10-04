package com.psychologyapi.psychologyapi.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;

/** Mensagem agendada, presa a uma Consulta (spec 5.2).
 *
 *  NAO TEM CAMPO DE CORPO, de proposito: a mensagem e montada no envio a
 *  partir da Consulta e dos blocos fixos do Usuario (5.5). Acrescentar um
 *  campo de texto livre aqui remove a garantia de "zero conteudo clinico"
 *  da 7.1 — o assunto e o corpo chegam na tela de bloqueio do celular do
 *  paciente, e ele nao escolheu isso. */
@Entity
@Table(name = "aviso_de_consulta", schema = "public")
@Data
public class AvisoDeConsulta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_aviso", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_consulta", nullable = false)
    private Consulta consulta;

    @Enumerated(EnumType.STRING)
    @Column(name = "destinatario_aviso", nullable = false, length = 20)
    private Destinatario destinatario;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal_aviso", nullable = false, length = 20)
    private CanalAviso canal;

    @Column(name = "agendado_para_aviso", nullable = false)
    private LocalDateTime agendadoPara;

    @Column(name = "data_hora_consulta_aviso", nullable = false)
    private LocalDateTime dataHoraConsulta;

    @Column(name = "tentativas_aviso", nullable = false)
    private int tentativas;

    @Column(name = "proxima_tentativa_aviso")
    private LocalDateTime proximaTentativa;

    @Column(name = "enviado_em_aviso")
    private LocalDateTime enviadoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_aviso", nullable = false, length = 20)
    private EstadoAviso estado;

    /** Motivo da falha. Diagnostico — nunca conteudo de mensagem. */
    @Column(name = "erro_aviso")
    private String erro;

    @PrePersist
    private void prePersist() {
        if (this.estado == null) {
            this.estado = EstadoAviso.PENDENTE;
        }
    }
}
