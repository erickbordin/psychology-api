package com.psychologyapi.psychologyapi.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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

    @Column(name = "valor_padrao_sessao_usuario", nullable = true, precision = 10, scale = 2)
    private BigDecimal valorPadraoSessao;

    @Enumerated(EnumType.STRING)
    @Column(name = "antecedencia_aviso_usuario", nullable = false, length = 20)
    private Antecedencia antecedencia;

    @Column(name = "titulo_no_email_usuario", nullable = false)
    private boolean tituloNoEmail;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "usuario_bloco_aviso",
            joinColumns = @JoinColumn(name = "id_usuario"))
    @Column(name = "bloco", length = 30)
    @Enumerated(EnumType.STRING)
    private Set<BlocoAviso> blocosAviso = new HashSet<>();

    @Column(name = "titulo_profissional_usuario", length = 80)
    private String tituloProfissional;

    @Column(name = "resumo_diario_usuario", nullable = false)
    private boolean resumoDiario = true;

    @Column(name = "expediente_inicio_usuario")
    private Integer expedienteInicio;

    @Column(name = "expediente_fim_usuario")
    private Integer expedienteFim;

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
        if (this.antecedencia == null) {
            this.antecedencia = Antecedencia.H24;
        }
        if (this.blocosAviso == null || this.blocosAviso.isEmpty()) {
            this.blocosAviso = new HashSet<>(Set.of(BlocoAviso.REMARCAR));
        }
    }

}
