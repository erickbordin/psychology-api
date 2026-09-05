package com.psychologyapi.psychologyapi.model;

/** Ciclo de vida do aviso. PENDENTE e o unico estado que o job reivindica. */
public enum EstadoAviso {
    PENDENTE,
    ENVIADO,
    FALHOU,
    CANCELADO
}
