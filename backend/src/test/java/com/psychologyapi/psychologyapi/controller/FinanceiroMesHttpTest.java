package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

@HttpTest
@Import(PainelHojeHttpTest.RelogioFixoConfig.class)
class FinanceiroMesHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private ConsultaRepository consultaRepository;

    private String tokenDoDono;
    private String tokenDeQuemNaoTemNada;

    private Consulta salvar(Usuario dono, Paciente paciente, String quando, Status status, String valor, String pagoEm) {
        Consulta consulta = Fixtures.consulta(dono, paciente, LocalDateTime.parse(quando), status);
        consulta.setValor(valor == null ? null : new BigDecimal(valor));
        consulta.setPagoEm(pagoEm == null ? null : LocalDateTime.parse(pagoEm));
        return consultaRepository.save(consulta);
    }

    @BeforeEach
    void preparar() {
        Usuario dono = usuarioRepository.save(Fixtures.usuario("dona.financeiro@example.com"));
        tokenDoDono = "Bearer " + tokenService.gerarToken(dono.getEmailUsuario());

        Paciente bruna = Fixtures.paciente(dono, "Bruna Teixeira");
        bruna.setValorSessao(new BigDecimal("180.00"));
        bruna = pacienteRepository.save(bruna);
        salvar(dono, bruna, "2026-12-01T09:00:00", Status.REALIZADA, "180.00", "2026-12-01T10:00:00");
        salvar(dono, bruna, "2026-12-08T09:00:00", Status.REALIZADA, "180.00", null);
        salvar(dono, bruna, "2026-12-15T09:00:00", Status.FALTOU, "180.00", null);
        salvar(dono, bruna, "2026-11-10T09:00:00", Status.REALIZADA, "150.00", null);

        Paciente ivan = pacienteRepository.save(Fixtures.paciente(dono, "Ivan Prado"));
        salvar(dono, ivan, "2026-12-05T09:00:00", Status.REALIZADA, null, null);

        Paciente clara = pacienteRepository.save(Fixtures.paciente(dono, "Clara Bueno"));
        salvar(dono, clara, "2026-12-10T09:00:00", Status.REALIZADA, "200.00", null);
        clara.setDeletedAt(LocalDateTime.parse("2026-12-20T09:00:00"));
        pacienteRepository.save(clara);

        Usuario outro = usuarioRepository.save(Fixtures.usuario("outro.financeiro@example.com"));
        Paciente doOutro = pacienteRepository.save(Fixtures.paciente(outro, "Paciente do outro psicologo"));
        salvar(outro, doOutro, "2026-12-09T09:00:00", Status.REALIZADA, "500.00", null);

        Usuario semNada = usuarioRepository.save(Fixtures.usuario("sem.nada.financeiro@example.com"));
        tokenDeQuemNaoTemNada = "Bearer " + tokenService.gerarToken(semNada.getEmailUsuario());
    }

    @Test
    @DisplayName("o mes por competencia: faturado, recebido, a receber e sessoes sem valor")
    void totaisDoMes() throws Exception {
        mockMvc.perform(get("/financeiro/mes?ano=2026&mes=12").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.faturado").value(560.0))
               .andExpect(jsonPath("$.recebido").value(180.0))
               .andExpect(jsonPath("$.aReceber").value(380.0))
               .andExpect(jsonPath("$.sessoesSemValor").value(1));
    }

    @Test
    @DisplayName("as sessoes do mes sao as REALIZADAS de dezembro, da mais cedo para a mais tarde")
    void sessoesDoMesEmOrdem() throws Exception {
        mockMvc.perform(get("/financeiro/mes?ano=2026&mes=12").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.sessoes.length()").value(4))
               .andExpect(jsonPath("$.sessoes[0].dtConsulta").value("2026-12-01T09:00:00"))
               .andExpect(jsonPath("$.sessoes[0].pacienteNome").value("Bruna Teixeira"))
               .andExpect(jsonPath("$.sessoes[3].pacienteNome").value("Clara Bueno"))
               .andExpect(jsonPath("$.sessoes[3].pacienteExcluido").value(true));
    }

    @Test
    @DisplayName("quem deve: soma de todos os meses, maior divida primeiro, paciente excluido marcado")
    void devendo() throws Exception {
        mockMvc.perform(get("/financeiro/mes?ano=2026&mes=12").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(jsonPath("$.devendo.length()").value(2))
               .andExpect(jsonPath("$.devendo[0].pacienteNome").value("Bruna Teixeira"))
               .andExpect(jsonPath("$.devendo[0].quantidadeSessoes").value(2))
               .andExpect(jsonPath("$.devendo[0].total").value(330.0))
               .andExpect(jsonPath("$.devendo[0].consultaIds.length()").value(2))
               .andExpect(jsonPath("$.devendo[1].pacienteNome").value("Clara Bueno"))
               .andExpect(jsonPath("$.devendo[1].pacienteExcluido").value(true))
               .andExpect(jsonPath("$.devendo[1].total").value(200.0));
    }

    @Test
    @DisplayName("sem ano nem mes, devolve o mes corrente do relogio")
    void semParametros() throws Exception {
        mockMvc.perform(get("/financeiro/mes").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.ano").value(2026))
               .andExpect(jsonPath("$.mes").value(12));
    }

    @Test
    @DisplayName("quem nao tem nada recebe 200 com zeros e listas vazias")
    void semNada() throws Exception {
        mockMvc.perform(get("/financeiro/mes?ano=2026&mes=12")
                       .header(HttpHeaders.AUTHORIZATION, tokenDeQuemNaoTemNada))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.faturado").value(0.0))
               .andExpect(jsonPath("$.recebido").value(0.0))
               .andExpect(jsonPath("$.aReceber").value(0.0))
               .andExpect(jsonPath("$.sessoes").isEmpty())
               .andExpect(jsonPath("$.devendo").isEmpty());
    }

    @Test
    @DisplayName("so o ano: 400 apontando o campo mes")
    void soAno() throws Exception {
        mockMvc.perform(get("/financeiro/mes?ano=2026").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[0].campo").value("mes"))
               .andExpect(jsonPath("$.erros[0].erro").value("Informe 'ano' e 'mes' juntos, ou nenhum dos dois."));
    }

    @Test
    @DisplayName("mes 13 e recusado no campo mes")
    void mesTreze() throws Exception {
        mockMvc.perform(get("/financeiro/mes?ano=2026&mes=13").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[0].campo").value("mes"));
    }

    @Test
    @DisplayName("mes que nao e numero: 400")
    void mesNaoNumerico() throws Exception {
        mockMvc.perform(get("/financeiro/mes?ano=2026&mes=abc").header(HttpHeaders.AUTHORIZATION, tokenDoDono))
               .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sem token: 401")
    void semToken() throws Exception {
        mockMvc.perform(get("/financeiro/mes?ano=2026&mes=12"))
               .andExpect(status().isUnauthorized());
    }
}
