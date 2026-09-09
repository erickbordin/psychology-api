package com.psychologyapi.psychologyapi.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Um Clock injetavel para que "hoje" e "agora" sejam testaveis sem esperar a
 *  hora certa do dia. Em producao e o relogio do sistema, e o fuso e o unico
 *  da spec 5.9, fixado no Dockerfile — entao o comportamento nao muda. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
