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
@Table(name = "anotacao", schema = "public")
public class Anotacao {
    
    @Column(name = "id_anotacao", nullable = false)
    private UUID id_anotacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @Column(name = "anotacao", nullable = false)
    private String anotacao;

    @Column(name = "created_at_anotacao", nullable = false)
    private LocalDateTime createdAt_anotacao;
}
