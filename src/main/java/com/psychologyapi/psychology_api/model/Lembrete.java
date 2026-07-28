package com.psychologyapi.psychology_api.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "lembrete", schema = "public")
public class Lembrete {

    @Column(name = "id_lembrete", nullable = false)
    private UUID id_lembrete;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @Column(name = "descricao", nullable = false)
    private String descricao;

    @Column(name = "created_at_lembrete", nullable = false)
    private LocalDateTime createdAt_lembrete;

}
