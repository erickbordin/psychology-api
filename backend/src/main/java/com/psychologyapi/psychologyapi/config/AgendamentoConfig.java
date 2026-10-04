package com.psychologyapi.psychologyapi.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnExpression("${app.avisos.habilitado:true} and !'${spring.mail.host:}'.isBlank()")
public class AgendamentoConfig {
}
