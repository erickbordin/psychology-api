package com.psychologyapi.psychologyapi.model;

/** Por onde o aviso de consulta chega ao paciente. WHATSAPP e da fase 2
 *  (spec 3); existe aqui porque o schema fecha antes das fatias. */
public enum CanalAviso {
    EMAIL,
    WHATSAPP,
    NENHUM
}
