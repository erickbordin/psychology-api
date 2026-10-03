package com.psychologyapi.psychologyapi.service.dinheiro;

import java.math.BigDecimal;

import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Status;

public final class ValorDaSessao {

    private ValorDaSessao() {
    }

    public static void congelarSeRealizada(Consulta consulta) {
        if (consulta.getStatus() != Status.REALIZADA || consulta.getValor() != null) {
            return;
        }
        BigDecimal doPaciente = consulta.getPaciente().getValorSessao();
        consulta.setValor(doPaciente != null ? doPaciente : consulta.getUsuario().getValorPadraoSessao());
    }
}
