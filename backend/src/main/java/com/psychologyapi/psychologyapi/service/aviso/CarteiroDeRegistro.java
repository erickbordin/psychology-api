package com.psychologyapi.psychologyapi.service.aviso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CarteiroDeRegistro implements Carteiro {

    private static final Logger LOG = LoggerFactory.getLogger(CarteiroDeRegistro.class);

    @Override
    public void enviar(Mensagem mensagem) {
        LOG.info("Aviso registrado sem SMTP configurado");
    }
}
