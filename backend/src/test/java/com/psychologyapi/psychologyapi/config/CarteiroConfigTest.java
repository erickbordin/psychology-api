package com.psychologyapi.psychologyapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.psychologyapi.psychologyapi.service.aviso.Carteiro;
import com.psychologyapi.psychologyapi.service.aviso.CarteiroDeRegistro;
import com.psychologyapi.psychologyapi.service.aviso.CarteiroSmtp;

class CarteiroConfigTest {

    private static final String MENSAGEM = "Defina APP_EMAIL_REMETENTE e APP_FRONTEND_URL antes de ligar o SMTP.";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CarteiroConfig.class)
            .withBean(JavaMailSenderImpl.class);

    @Test
    void semHostUsaCarteiroDeRegistro() {
        runner.withPropertyValues(
                "spring.mail.host=",
                "app.email.remetente=lembretes@localhost",
                "app.frontend.url=http://localhost:5173")
                .run(ctx -> assertThat(ctx.getBean(Carteiro.class)).isInstanceOf(CarteiroDeRegistro.class));
    }

    @Test
    void comHostEVariaveisConfiguradasUsaCarteiroSmtp() {
        runner.withPropertyValues(
                "spring.mail.host=smtp.exemplo.test",
                "app.email.remetente=avisos@exemplo.test",
                "app.frontend.url=https://app.exemplo.test")
                .run(ctx -> assertThat(ctx.getBean(Carteiro.class)).isInstanceOf(CarteiroSmtp.class));
    }

    @Test
    void comHostERemetentePadraoFalhaNoBoot() {
        runner.withPropertyValues(
                "spring.mail.host=smtp.exemplo.test",
                "app.email.remetente=lembretes@localhost",
                "app.frontend.url=https://app.exemplo.test")
                .run(ctx -> assertThat(ctx).hasFailed().getFailure().hasRootCauseMessage(MENSAGEM));
    }

    @Test
    void comHostEFrontendPadraoFalhaNoBoot() {
        runner.withPropertyValues(
                "spring.mail.host=smtp.exemplo.test",
                "app.email.remetente=avisos@exemplo.test",
                "app.frontend.url=http://localhost:5173")
                .run(ctx -> assertThat(ctx).hasFailed().getFailure().hasRootCauseMessage(MENSAGEM));
    }
}
