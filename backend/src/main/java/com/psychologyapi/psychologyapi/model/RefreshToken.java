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

/**
 * Credencial de renovacao de sessao. Guarda o SHA-256 do token, nunca o token:
 * um dump do banco vazado nao vira conjunto de sessoes ativas.
 *
 * Unica tabela do projeto sem deleted_at, de proposito: revokedAt ja cobre o
 * encerramento logico, e token vencido e lixo de infraestrutura, nao prontuario.
 */
@Entity
@Table(name = "refresh_token", schema = "public")
@Data
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_refresh_token", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "hash_token", nullable = false, unique = true, length = 64)
    private String hashToken;

    @Column(name = "created_at_refresh_token", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at_refresh_token", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at_refresh_token")
    private LocalDateTime revokedAt;

    /** Proximo elo da cadeia de rotacao. UUID solto, e nao @ManyToOne, para nao
     *  criar auto-relacionamento de entidade por um dado que so se le em auditoria. */
    @Column(name = "substituido_por")
    private UUID substituidoPor;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
