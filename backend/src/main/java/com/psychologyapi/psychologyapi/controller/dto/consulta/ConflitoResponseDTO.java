package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.util.UUID;

/** Resposta de GET /consultas/conflito (spec 5.6).
 *
 *  `pacienteNome` e `consultaId` vem nulos quando o horario esta livre. O
 *  nome vai junto de proposito: dizer so "ocupado" obrigaria o psicologo a
 *  ir procurar na agenda quem esta la. */
public record ConflitoResponseDTO(
        boolean livre,
        String pacienteNome,
        UUID consultaId) {

    public static ConflitoResponseDTO horarioLivre() {
        return new ConflitoResponseDTO(true, null, null);
    }
}
