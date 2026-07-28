package com.psychologyapi.psychology_api.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "paciente", schema = "public")
@Data
public class Paciente {
    
    @Column(name = "id_paciente", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id_paciente; 

    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "nm_paciente", nullable = false, length = 100)
    private String nome_paciente;
    
    @Column(name = "telefone_paciente", nullable = true, length = 20)
    private String telefone_paciente;

    @Column(name = "email_paciente", nullable = true, length = 100)
    private String email_paciente;

    @Column(name = "dt_nascimento_paciente", nullable = true)
    private LocalDate data_nascimento_paciente; 

    @Column(name = "observacoes_paciente", nullable = true)
    private String observacoes_paciente;

    @Column(name = "created_at_paciente", nullable = false)
    private LocalDateTime createdAt_paciente;

    @OneToMany(mappedBy = "paciente")
    private List<Anotacao> anotacoes;

    @OneToMany(mappedBy = "paciente")
    private List<Consulta> consultas;

    @OneToMany(mappedBy = "paciente")
    private List<Lembrete> lembretes;
}
