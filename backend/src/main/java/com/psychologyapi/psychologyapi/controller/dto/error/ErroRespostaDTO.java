package com.psychologyapi.psychologyapi.controller.dto.error;

import java.util.List;

public record ErroRespostaDTO(
    int status,
    String mensagem,
    List<ErroCampoDTO> erros
) {}
