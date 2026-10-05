package com.psychologyapi.psychologyapi.authsecurity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class LimiteDeTentativasTest {

    private static final class RelogioAjustavel extends Clock {
        private Instant agora = Instant.parse("2026-10-04T12:00:00Z");

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RelogioAjustavel relogio;
    private LimiteDeTentativas filtro;

    @BeforeEach
    void preparar() {
        relogio = new RelogioAjustavel();
        filtro = new LimiteDeTentativas(objectMapper, relogio, 10);
    }

    private MockHttpServletResponse chamar(String metodo, String rota, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, rota);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filtro.doFilter(request, response, chain);
        return response;
    }

    private int janelasAbertas() {
        return ((java.util.Map<?, ?>) ReflectionTestUtils.getField(filtro, "janelas")).size();
    }

    private void chamarVezes(int vezes, String rota, String ip) throws Exception {
        for (int i = 0; i < vezes; i++) {
            assertThat(chamar("POST", rota, ip).getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("dez tentativas passam e a décima primeira recebe 429")
    void decimaPrimeiraRecebe429() throws Exception {
        chamarVezes(10, "/auth/login", "10.0.0.1");

        assertThat(chamar("POST", "/auth/login", "10.0.0.1").getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("o 429 sai no envelope de erro com a mensagem fixa e a lista de erros vazia")
    void envelopeDoLimite() throws Exception {
        chamarVezes(10, "/auth/login", "10.0.0.1");

        MockHttpServletResponse resposta = chamar("POST", "/auth/login", "10.0.0.1");
        JsonNode corpo = objectMapper.readTree(resposta.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(resposta.getContentType()).startsWith("application/json");
        assertThat(corpo.get("status").asInt()).isEqualTo(429);
        assertThat(corpo.get("mensagem").asText()).isEqualTo("Muitas tentativas. Tente de novo em alguns minutos.");
        assertThat(corpo.get("erros").isArray()).isTrue();
        assertThat(corpo.get("erros")).isEmpty();
    }

    @Test
    @DisplayName("uma nova janela de sessenta segundos libera o IP")
    void novaJanelaLibera() throws Exception {
        chamarVezes(10, "/auth/login", "10.0.0.1");
        assertThat(chamar("POST", "/auth/login", "10.0.0.1").getStatus()).isEqualTo(429);

        relogio.avancar(Duration.ofSeconds(60));

        assertThat(chamar("POST", "/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("dentro dos sessenta segundos a janela continua bloqueada")
    void dentroDaJanelaContinuaBloqueado() throws Exception {
        chamarVezes(10, "/auth/login", "10.0.0.1");

        relogio.avancar(Duration.ofSeconds(59));

        assertThat(chamar("POST", "/auth/login", "10.0.0.1").getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("rotas diferentes contam separado para o mesmo IP")
    void rotasContamSeparado() throws Exception {
        chamarVezes(10, "/auth/login", "10.0.0.1");

        assertThat(chamar("POST", "/auth/registrar", "10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(chamar("POST", "/auth/refresh", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("IPs diferentes contam separado na mesma rota")
    void ipsContamSeparado() throws Exception {
        chamarVezes(10, "/auth/login", "10.0.0.1");

        assertThat(chamar("POST", "/auth/login", "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("rota fora da lista nunca conta")
    void rotaForaDaListaNuncaConta() throws Exception {
        for (int i = 0; i < 50; i++) {
            assertThat(chamar("POST", "/pacientes", "10.0.0.1").getStatus()).isEqualTo(200);
            assertThat(chamar("POST", "/auth/logout", "10.0.0.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("metodo diferente de POST na rota da lista nunca conta")
    void metodoForaDoPostNuncaConta() throws Exception {
        for (int i = 0; i < 50; i++) {
            assertThat(chamar("GET", "/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("requisicao bloqueada nao chega ao destino")
    void bloqueadaNaoChegaAoDestino() throws Exception {
        chamarVezes(10, "/auth/login", "10.0.0.1");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filtro.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("janelas vencidas sao descartadas a cada chamada")
    void janelasVencidasSaoDescartadas() throws Exception {
        for (int i = 0; i < 20; i++) {
            chamar("POST", "/auth/login", "10.0.1." + i);
        }
        assertThat(janelasAbertas()).isEqualTo(20);

        relogio.avancar(Duration.ofSeconds(61));
        chamar("POST", "/auth/login", "10.0.9.9");

        assertThat(janelasAbertas()).isEqualTo(1);
    }
}
