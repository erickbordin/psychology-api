package com.psychologyapi.psychologyapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.support.CarteiroDeTeste;
import com.psychologyapi.psychologyapi.support.PostgresContainerConfig;

@SpringBootTest(properties = {
        "JWT_SECRET=c2VncmVkb2RldGVzdGVjb21wcmlkb29zdWZpY2llbnRlcGFyYWhtYWNzaGEyNTY=",
        "app.avisos.habilitado=false",
        "app.termos.diretorio=classpath:termos-teste/",
        "app.cadastro.convites=corrida-1,corrida-2,corrida-3",
        "app.limite.por-minuto=100000"
})
@Import({PostgresContainerConfig.class, CarteiroDeTeste.class})
@AutoConfigureMockMvc
class ConviteConcorrenteHttpTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;

    private final String sufixo = UUID.randomUUID().toString();

    @AfterEach
    void limpar() {
        String filtro = "%" + sufixo + "%";
        jdbc.update("delete from convite_usado where id_usuario in (select id_usuario from usuario where email_usuario like ?)", filtro);
        jdbc.update("delete from log_auditoria where id_usuario in (select id_usuario from usuario where email_usuario like ?)", filtro);
        jdbc.update("delete from refresh_token where id_usuario in (select id_usuario from usuario where email_usuario like ?)", filtro);
        jdbc.update("delete from usuario_bloco_aviso where id_usuario in (select id_usuario from usuario where email_usuario like ?)", filtro);
        jdbc.update("delete from usuario where email_usuario like ?", filtro);
    }

    private int registrar(String email, CountDownLatch largada) throws Exception {
        return registrar("corrida-1", email, largada);
    }

    private int registrar(String codigo, String email, CountDownLatch largada) throws Exception {
        largada.await();
        return mockMvc.perform(post("/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoConvite\":\"" + codigo + "\",\"aceiteTermos\":true,\"versaoTermos\":\"teste-1\",\"nome\":\"Psicologa Corrida\",\"email\":\""
                        + email + "\",\"senha\":\"senhaforte123\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("dois cadastros simultaneos com o mesmo convite resultam em um 201 e um 403")
    void corridaPeloMesmoConvite() throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> resultados = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String email = "corrida" + i + "-" + sufixo + "@teste.com";
                resultados.add(pool.submit(() -> registrar(email, largada)));
            }
            largada.countDown();

            List<Integer> status = new ArrayList<>();
            for (Future<Integer> f : resultados) {
                status.add(f.get());
            }

            assertThat(status).containsExactlyInAnyOrder(201, 403);
            Integer usuarios = jdbc.queryForObject(
                    "select count(*) from usuario where email_usuario like ?", Integer.class, "%" + sufixo + "%");
            assertThat(usuarios).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("dois cadastros simultaneos com o mesmo e-mail e convites diferentes resultam em 201 e 409, e so o convite do vencedor e queimado")
    void corridaPeloMesmoEmail() throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        String email = "mesmo-" + sufixo + "@teste.com";
        try {
            Future<Integer> a = pool.submit(() -> registrar("corrida-2", email, largada));
            Future<Integer> b = pool.submit(() -> registrar("corrida-3", email, largada));
            largada.countDown();

            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(201, 409);
            Integer queimados = jdbc.queryForObject(
                    "select count(*) from convite_usado c join usuario u on u.id_usuario = c.id_usuario where u.email_usuario like ?",
                    Integer.class, "%" + sufixo + "%");
            assertThat(queimados).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
