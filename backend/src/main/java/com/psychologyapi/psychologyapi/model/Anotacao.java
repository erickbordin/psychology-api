package com.psychologyapi.psychologyapi.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "anotacao", schema = "public")
@Data
public class Anotacao {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_anotacao", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    /** A sessao a que esta anotacao pertence. Nulo = anotacao avulsa: vale,
     *  mas nao entra na linha do tempo da ficha (spec 5.1). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_consulta", nullable = true)
    private Consulta consulta;

    @Column(name = "anotacao", nullable = false)
    private String conteudo;

    @Column(name = "created_at_anotacao", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at_anotacao")
    private LocalDateTime deletedAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

}
