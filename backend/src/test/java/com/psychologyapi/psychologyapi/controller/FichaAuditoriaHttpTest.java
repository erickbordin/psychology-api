package com.psychologyapi.psychologyapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.LogAuditoria;
import com.psychologyapi.psychologyapi.repository.LogAuditoriaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.PostgresContainerConfig;

/**
 * Abrir a ficha e acesso a prontuario, e acesso a prontuario grava trilha
 * (spec 4 (5): "com dado de saude, registro de acesso e ativo, nao enfeite").
 *
 * <h2>Por que esta classe existe separada, e por que ela NAO e @HttpTest</h2>
 *
 * A anotacao {@code @HttpTest} carrega {@code @Transactional}, e e justamente
 * isso que cega o teste para o defeito que esta classe guarda. O plano da
 * Tarefa 3 mandava anotar {@code FichaService.doPaciente} com
 * {@code @Transactional(readOnly = true)}. Em producao aquilo DESCARTAVA o log
 * de auditoria em silencio: {@code readOnly} poe a sessao do Hibernate em
 * {@code FlushMode.MANUAL}, o {@code save()} do log nunca e flushado no commit,
 * e nenhuma excecao e levantada. Nada quebrava; a trilha simplesmente nao
 * existia.
 *
 * Dentro de um teste {@code @Transactional}, esse defeito e INVISIVEL: a
 * transacao do teste ja esta aberta em modo de escrita e a transacao
 * participante do service ignora o {@code readOnly}. O teste ficaria verde
 * afirmando exatamente aquilo que a producao nao faz — o pior tipo de teste,
 * porque ele defende um defeito.
 *
 * Por isso aqui nao ha rollback: o {@code @AfterEach} limpa a mao. E o preco
 * de medir o efeito em vez do marcador.
 */
@SpringBootTest(properties = "JWT_SECRET=c2VncmVkb2RldGVzdGVjb21wcmlkb29zdWZpY2llbnRlcGFyYWhtYWNzaGEyNTY=")
@Import(PostgresContainerConfig.class)
@AutoConfigureMockMvc
class FichaAuditoriaHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private LogAuditoriaRepository logAuditoriaRepository;

    private Usuario dono;
    private Paciente paciente;
    private String token;

    @BeforeEach
    void preparar() {
        dono = usuarioRepository.save(Fixtures.usuario("dona.trilha@example.com"));
        token = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());
        paciente = pacienteRepository.save(Fixtures.paciente(dono, "Helena Vaz"));
    }

    @AfterEach
    void limpar() {
        // sem @Transactional nao ha rollback: o que este teste escreve, este
        // teste apaga. Ordem ditada pelas chaves estrangeiras: log, depois
        // paciente, depois usuario
        logAuditoriaRepository.deleteAll(
                logAuditoriaRepository.findByUsuarioOrderByCreatedAtDesc(dono, PageRequest.of(0, 100)).getContent());
        pacienteRepository.delete(paciente);
        usuarioRepository.delete(dono);
    }

    @Test
    @DisplayName("abrir a ficha grava a trilha de acesso ao prontuario, de verdade")
    void abrirFichaGravaTrilha() throws Exception {
        mockMvc.perform(get("/pacientes/" + paciente.getId() + "/ficha")
                        .header(HttpHeaders.AUTHORIZATION, token))
               .andExpect(status().isOk());

        var logs = logAuditoriaRepository
                .findByUsuarioAndEntidadeAndEntidadeIdOrderByCreatedAtDesc(
                        dono, "Paciente", paciente.getId(), PageRequest.of(0, 10))
                .getContent();

        assertThat(logs)
                .as("a ficha foi aberta e nenhuma linha de trilha chegou ao banco — "
                        + "confira se FichaService.doPaciente voltou a ser readOnly")
                .isNotEmpty();
        assertThat(logs).extracting(LogAuditoria::getEntidade).contains("Paciente");
    }
}
