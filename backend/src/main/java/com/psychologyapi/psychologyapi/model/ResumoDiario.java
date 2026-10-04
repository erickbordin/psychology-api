package com.psychologyapi.psychologyapi.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "resumo_diario", schema = "public")
@Data
public class ResumoDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_resumo", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "data_resumo", nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_resumo", nullable = false, length = 20)
    private EstadoAviso estado;

    @Column(name = "tentativas_resumo", nullable = false)
    private int tentativas;

    @Column(name = "proxima_tentativa_resumo")
    private LocalDateTime proximaTentativa;

    @Column(name = "enviado_em_resumo")
    private LocalDateTime enviadoEm;

    @Column(name = "erro_resumo")
    private String erro;

    @Column(name = "created_at_resumo", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.estado == null) {
            this.estado = EstadoAviso.PENDENTE;
        }
    }
}
