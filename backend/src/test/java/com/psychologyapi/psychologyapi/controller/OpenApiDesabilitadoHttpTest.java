package com.psychologyapi.psychologyapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.support.PostgresContainerConfig;

/**
 * SPRINGDOC_ENABLED=false e o interruptor que apaga o contrato OpenAPI em
 * producao (spec 9, gate da Fatia 5). {@link OpenApiHttpTest} prova o
 * contrato ligado; esta classe prova o desligado — sem ela, quem renomear a
 * propriedade ou reorganizar a config so descobre em producao, com o
 * interruptor mudo e a suite inteira verde.
 *
 * Nao usa a anotacao composta {@code @HttpTest}: o properties diferente
 * (SPRINGDOC_ENABLED=false) muda a chave de cache do contexto do Spring, e
 * portanto sobe seu PROPRIO contexto e seu PROPRIO container Postgres via
 * {@link PostgresContainerConfig} — o mesmo custo ja pago por
 * PainelHojeHttpTest por causa do relogio fixo. Preco assumido de proposito
 * (ver fix round 3a6): testar o controle que mantem a API fora do ar em
 * producao vale um container extra.
 */
@SpringBootTest(properties = {
        "JWT_SECRET=c2VncmVkb2RldGVzdGVjb21wcmlkb29zdWZpY2llbnRlcGFyYWhtYWNzaGEyNTY=",
        "SPRINGDOC_ENABLED=false"
})
@Import(PostgresContainerConfig.class)
@AutoConfigureMockMvc
@Transactional
class OpenApiDesabilitadoHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("com SPRINGDOC_ENABLED=false o contrato OpenAPI nao e servido")
    void contratoNaoEServidoComSpringdocDesligado() throws Exception {
        // Nao fixa o codigo exato: com a rota desregistrada o springdoc 2.8.9
        // responde 404, mas um 401 tambem significaria "nao servido" e
        // satisfaria o mesmo gate. O que importa e o contrato nao sair — 200
        // e a unica resposta que reabriria a rota em producao.
        int statusCode = mockMvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getStatus();

        assertThat(statusCode).isNotEqualTo(200);
    }

    @Test
    @DisplayName("com SPRINGDOC_ENABLED=false a Swagger UI tambem nao e servida")
    void swaggerUiNaoEServidaComSpringdocDesligado() throws Exception {
        int statusCode = mockMvc.perform(get("/swagger-ui/index.html"))
                .andReturn().getResponse().getStatus();

        assertThat(statusCode).isNotEqualTo(200);
    }
}
