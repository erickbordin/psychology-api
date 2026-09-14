package com.psychologyapi.psychologyapi.controller.dto.ficha;

import java.util.List;

import com.psychologyapi.psychologyapi.controller.dto.paciente.PacienteResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.pendencia.PendenciaResponseDTO;

/** A tela (3) inteira numa chamada, como o painel de spec 5.6 faz com a (1). */
public record FichaDoPacienteDTO(
        PacienteResponseDTO paciente,
        List<SessaoDaFichaDTO> linhaDoTempo,
        List<PendenciaResponseDTO> pendencias) {
}
