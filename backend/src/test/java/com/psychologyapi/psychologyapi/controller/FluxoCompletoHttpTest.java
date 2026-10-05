package com.psychologyapi.psychologyapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * Prova que os endpoints compoem de ponta a ponta sobre HTTP: registrar,
 * logar, usar O TOKEN DEVOLVIDO PELO LOGIN (nunca um token minerado direto
 * do TokenService) numa rota protegida, cadastrar paciente, anotar e ler a
 * anotacao de volta na listagem paginada do paciente.
 *
 * Nenhum teste existente percorre essa cadeia: cada camada e testada
 * isoladamente com um token forjado via TokenService. Este teste e o unico
 * que provaria, por exemplo, um token que o /auth/login devolve mas que o
 * filtro JWT rejeita, ou um /auth/registrar que "funciona" sem produzir uma
 * conta utilizavel no /auth/login.
 */
@HttpTest
class FluxoCompletoHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("registrar -> logar -> cadastrar paciente -> anotar -> listar: cada etapa usa o resultado real da anterior")
    void fluxoCompletoDeRegistroAtePrimeiraAnotacao() throws Exception {
        String email = "beatriz.fluxo@teste.com";
        String senha = "senhaForte123";

        // 1. Registrar o psicologo.
        mockMvc.perform(post("/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"codigoConvite":"teste-1","aceiteTermos":true,"versaoTermos":"teste-1","nome":"Psicologa Beatriz","email":"%s","senha":"%s"}
                        """.formatted(email, senha)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailUsuario").value(email));

        // 2. Logar com a MESMA conta e capturar o token que o endpoint devolve.
        String corpoDoLogin = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"emailUsuario":"%s","senha":"%s"}
                        """.formatted(email, senha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(corpoDoLogin, "$.token");
        String bearer = "Bearer " + token;

        // 3. Usar ESSE token (devolvido pelo login, nao minerado via TokenService)
        // numa rota protegida: cadastrar um paciente.
        String corpoDoPaciente = mockMvc.perform(post("/pacientes")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"aceiteTermos":true,"versaoTermos":"teste-1","nome":"Carlos Fluxo","telefone":"51999998888","email":"carlos@teste.com","dataNascimento":"1990-05-20"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Carlos Fluxo"))
                .andReturn().getResponse().getContentAsString();

        String pacienteId = JsonPath.read(corpoDoPaciente, "$.idPaciente");
        assertThat(pacienteId).isNotBlank();

        // 4. Escrever uma anotacao de consulta para ESSE paciente, com o mesmo token.
        String conteudoDaAnotacao = "Primeira sessao: paciente relatou melhora no sono.";
        String corpoDaAnotacao = mockMvc.perform(post("/pacientes/" + pacienteId + "/anotacoes")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"anotacao":"%s"}
                        """.formatted(conteudoDaAnotacao)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conteudo").value(conteudoDaAnotacao))
                .andExpect(jsonPath("$.pacienteId").value(pacienteId))
                .andReturn().getResponse().getContentAsString();

        String anotacaoId = JsonPath.read(corpoDaAnotacao, "$.id");
        assertThat(anotacaoId).isNotBlank();

        // 5. Ler a anotacao de volta na listagem paginada do paciente e conferir o
        // envelope do PagedModel.
        mockMvc.perform(get("/pacientes/" + pacienteId + "/anotacoes")
                .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(anotacaoId))
                .andExpect(jsonPath("$.content[0].conteudo").value(conteudoDaAnotacao))
                .andExpect(jsonPath("$.content[0].pacienteId").value(pacienteId))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1));
    }
}
