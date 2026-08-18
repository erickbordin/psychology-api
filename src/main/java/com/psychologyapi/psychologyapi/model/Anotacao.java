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
    @Column(name = "id_anotacao", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @Column(name = "anotacao", nullable = false)
    private String conteudo;

    @Column(name = "created_at_anotacao", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at_anotacao", nullable = false)
    private LocalDateTime deletedAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

}
