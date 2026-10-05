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
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "convite_usado", schema = "public")
@Data
public class ConviteUsado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_convite", nullable = false)
    private UUID id;

    @Column(name = "hash_convite", nullable = false, length = 64, unique = true)
    private String hash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "usado_em_convite", nullable = false)
    private LocalDateTime usadoEm;

}
