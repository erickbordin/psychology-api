package com.psychologyapi.psychologyapi.service.aviso;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnExpression("${app.avisos.habilitado:true} and !'${spring.mail.host:}'.isBlank()")
@RequiredArgsConstructor
public class AgendadorDeAvisos {

    private final ServicoDeAvisos servicoDeAvisos;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void executar() {
        servicoDeAvisos.passada();
    }
}
