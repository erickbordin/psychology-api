package com.psychologyapi.psychologyapi.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "paciente", schema = "public")
@Data
public class Paciente {

    @Id
    @Column(name = "id_paciente", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "nm_paciente", nullable = false, length = 100)
    private String nomePaciente;

    @Column(name = "telefone_paciente", nullable = true, length = 20)
    private String telefonePaciente;

    @Column(name = "email_paciente", nullable = true, length = 100)
    private String emailPaciente;

    @Column(name = "dt_nascimento_paciente", nullable = true)
    private LocalDate dataNascimentoPaciente;

    @Column(name = "observacoes_paciente", nullable = true)
    private String observacoesPaciente;

    @Column(name = "created_at_paciente", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "paciente")
    private List<Anotacao> anotacoes;

    @OneToMany(mappedBy = "paciente")
    private List<Consulta> consultas;

    @OneToMany(mappedBy = "paciente")
    private List<Lembrete> lembretes;

    @Column(name = "deleted_at_paciente", nullable = false)
    private LocalDateTime deletedAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
