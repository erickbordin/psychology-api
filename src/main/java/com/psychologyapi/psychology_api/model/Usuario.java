package com.psychologyapi.psychology_api.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "usuario", schema = "public")
@Data
public class Usuario {

    @Id
    @Column(name = "id_usuario", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id_usuario;    

    @Column(name = "nm_usuario", nullable = false, length = 100)
    private String nome_usuario;
    
    @Column(name = "email_usuario", nullable = false, length = 100)
    private String email_usuario;

    @Column(name = "senha_usuario", nullable = false)
    private String senha_usuario;

    @Column(name = "created_at_usuario", nullable = false)
    private LocalDateTime createdAt_usuario;

    @OneToMany(mappedBy = "usuario")
    private List<Anotacao> anotacoes;

    @OneToMany(mappedBy = "usuario")
    private List<Consulta> consultas;

    @OneToMany(mappedBy = "usuario")
    private List<Paciente> pacientes;
}
