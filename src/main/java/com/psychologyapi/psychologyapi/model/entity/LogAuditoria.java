package com.psychologyapi.psychologyapi.model.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.psychologyapi.psychologyapi.model.Usuario;

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
@Table(name = "log_auditoria", schema = "public")
@Data
public class LogAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_log", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "acao", nullable = false)
    private AcaoAuditoria acao;

    @Column(name = "entidade", nullable = false, length = 50)
    private String entidade;

    @Column(name = "id_entidade", nullable = false)
    private UUID entidadeId;

    @Column(name = "created_at_log", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}