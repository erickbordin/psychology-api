package com.psychologyapi.psychologyapi.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import com.psychologyapi.psychologyapi.service.aviso.Carteiro;
import com.psychologyapi.psychologyapi.service.aviso.CarteiroDeRegistro;
import com.psychologyapi.psychologyapi.service.aviso.CarteiroSmtp;

@Configuration
public class CarteiroConfig {

    private static final String REMETENTE_PADRAO = "lembretes@localhost";
    private static final String FRONTEND_PADRAO = "http://localhost:5173";

    @Bean
    public Carteiro carteiro(
            @Value("${spring.mail.host:}") String host,
            @Value("${app.email.remetente:" + REMETENTE_PADRAO + "}") String remetente,
            @Value("${app.frontend.url:" + FRONTEND_PADRAO + "}") String frontendUrl,
            ObjectProvider<JavaMailSender> sender) {
        if (host.isBlank()) {
            return new CarteiroDeRegistro();
        }
        if (REMETENTE_PADRAO.equals(remetente) || FRONTEND_PADRAO.equals(frontendUrl)) {
            throw new IllegalStateException("Defina APP_EMAIL_REMETENTE e APP_FRONTEND_URL antes de ligar o SMTP.");
        }
        return new CarteiroSmtp(sender.getObject());
    }
}
