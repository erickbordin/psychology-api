package com.psychologyapi.psychologyapi.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Column(name = "valor_sessao_paciente", nullable = true, precision = 10, scale = 2)
    private BigDecimal valorSessao;

    @Column(name = "consentimento_aviso_em_paciente", nullable = true)
    private LocalDateTime consentimentoAvisoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal_aviso_paciente", nullable = false, length = 20)
    private CanalAviso canalAviso;

    @Column(name = "token_descadastro_paciente", nullable = false, unique = true, length = 64)
    private String tokenDescadastro;

    @Column(name = "created_at_paciente", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "paciente")
    private List<Anotacao> anotacoes;

    @OneToMany(mappedBy = "paciente")
    private List<Consulta> consultas;

    @OneToMany(mappedBy = "paciente")
    private List<Pendencia> pendencias;

    @Column(name = "descadastrado_em_paciente")
    private LocalDateTime descadastradoEm;

    @Column(name = "deleted_at_paciente")
    private LocalDateTime deletedAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.canalAviso == null) {
            this.canalAviso = CanalAviso.NENHUM;
        }
        if (this.tokenDescadastro == null) {
            // 32 bytes de aleatoriedade criptografica em Base64 URL-safe: cabe
            // num link sem escape e nao e adivinhavel a partir do id
            byte[] bytes = new byte[32];
            new java.security.SecureRandom().nextBytes(bytes);
            this.tokenDescadastro = java.util.Base64.getUrlEncoder()
                    .withoutPadding().encodeToString(bytes);
        }
    }
}
