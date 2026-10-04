package com.psychologyapi.psychologyapi.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.psychologyapi.psychologyapi.service.aviso.AgendadorDeAvisos;
import com.psychologyapi.psychologyapi.service.aviso.ServicoDeAvisos;

class AgendamentoConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AgendamentoConfig.class, AgendadorDeAvisos.class)
            .withBean(ServicoDeAvisos.class, () -> mock(ServicoDeAvisos.class));

    @Test
    void habilitadoSemHostNaoCriaAgendador() {
        runner.withPropertyValues("app.avisos.habilitado=true", "spring.mail.host=")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(AgendadorDeAvisos.class));
    }

    @Test
    void habilitadoComHostCriaAgendador() {
        runner.withPropertyValues("app.avisos.habilitado=true", "spring.mail.host=smtp.exemplo.test")
                .run(ctx -> assertThat(ctx).hasSingleBean(AgendadorDeAvisos.class));
    }

    @Test
    void desabilitadoComHostNaoCriaAgendador() {
        runner.withPropertyValues("app.avisos.habilitado=false", "spring.mail.host=smtp.exemplo.test")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(AgendadorDeAvisos.class));
    }
}
