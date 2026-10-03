package com.psychologyapi.psychologyapi.repository.projecao;

import java.math.BigDecimal;

public interface TotaisFinanceirosDoMes {

    BigDecimal getFaturado();

    BigDecimal getRecebido();

    Long getSemValor();
}
