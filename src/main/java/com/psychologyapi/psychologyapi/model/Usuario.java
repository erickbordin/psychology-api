package com.psychologyapi.psychologyapi.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "usuario", schema = "public")
@Data
public class Usuario {

    @Id
    @Column(name = "id_usuario", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;    

    @Column(name = "nm_usuario", nullable = false, length = 100)
    private String nomeUsuario;
    
    @Column(name = "email_usuario", nullable = false, length = 100, unique = true)
    private String emailUsuario;

    @Column(name = "senha_usuario", nullable = false)
    private String senhaUsuario;

    @Column(name = "created_at_usuario", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "usuario")
    private List<Anotacao> anotacoes;

    @OneToMany(mappedBy = "usuario")
    private List<Consulta> consultas;

    @OneToMany(mappedBy = "usuario")
    private List<Paciente> pacientes;

    @PrePersist
    private void prePersist(){
        this.createdAt = LocalDateTime.now();
    }

}
