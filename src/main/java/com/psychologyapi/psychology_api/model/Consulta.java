package com.psychologyapi.psychology_api.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "consulta", schema = "public")
public class Consulta {
    
    @Id
    @Column(name = "id_consulta", nullable = false)
    private UUID id_consulta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @Column(name = "data_hora_consulta", nullable = false)
    private LocalDateTime data_hora_consulta;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_consulta", nullable = false)
    private Status status;

    @Column(name = "created_at_consulta", nullable = false)
    private LocalDateTime createdAt_consulta;

}
