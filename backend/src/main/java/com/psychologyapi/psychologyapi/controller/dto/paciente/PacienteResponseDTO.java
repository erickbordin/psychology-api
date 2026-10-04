package com.psychologyapi.psychologyapi.controller.dto.paciente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.psychologyapi.psychologyapi.model.CanalAviso;

public record PacienteResponseDTO(

    UUID idPaciente,
    UUID idUsuario,
    String nome,
    String email,
    String telefone,
    LocalDate dataNascimento,

    /** Padrao que a consulta copia (spec 5.4). Nulo = psicologo ainda nao
     *  definiu valor para este paciente. */
    BigDecimal valorSessao,
    CanalAviso canalAviso,

    /** Quando o paciente consentiu em receber aviso. Nulo = nao consentiu, e
     *  entao nenhum aviso e agendado (invariante de 8.4). So de leitura aqui:
     *  consentir e um ato, nao um campo de formulario — a acao chega na
     *  Fatia 4. */
    LocalDateTime consentimentoAvisoEm,

    LocalDateTime createdAt,

    LocalDateTime descadastradoEm

) {}
