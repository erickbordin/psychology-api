package com.psychologyapi.psychologyapi.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * Fecha a tabela de validacao da spec 6.3 contra os dois DTOs de paciente:
 * nome, e-mail, telefone e data de nascimento. Classe dedicada, separada da
 * {@link SegurancaHttpTest} — o javadoc dela escopa a classe para filtro JWT,
 * cadeia de seguranca e {@link com.psychologyapi.psychologyapi.controller.common.GlobalExceptionsHandler},
 * e regra de validacao e outro assunto.
 */
@HttpTest
class PacienteValidacaoHttpTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PacienteRepository pacienteRepository;

    private Usuario psicologo(String email) {
        return usuarioRepository.save(Fixtures.usuario(email));
    }

    private String bearerDe(Usuario usuario) {
        return "Bearer " + tokenService.gerarToken(usuario.getEmailUsuario());
    }

    @Test
    @DisplayName("e-mail de paciente em formato invalido responde 400 (spec 6.1)")
    void emailDePacienteInvalidoResponde400() throws Exception {
        mockMvc.perform(post("/pacientes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(psicologo("dona.pacientes@example.com")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"nome":"Helena Vaz","email":"helena-arroba-nada"}
                         """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("email")));
    }

    @Test
    @DisplayName("nome de uma letra so responde 400: minimo e 2 (spec 6.3)")
    void nomeCurtoDemaisResponde400() throws Exception {
        mockMvc.perform(post("/pacientes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(psicologo("dona.pacientes@example.com")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"nome":"H"}
                         """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("nome")));
    }

    @Test
    @DisplayName("nascimento com idade acima de 120 anos responde 400 (spec 6.3)")
    void idadeAcimaDe120Responde400() throws Exception {
        String muitoAntiga = java.time.LocalDate.now().minusYears(121).toString();
        mockMvc.perform(post("/pacientes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(psicologo("dona.pacientes@example.com")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Helena Vaz\",\"dataNascimento\":\"" + muitoAntiga + "\"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("dataNascimento")));
    }

    @Test
    @DisplayName("telefone fora do formato brasileiro responde 400 (spec 6.3)")
    void telefoneForaDoFormatoResponde400() throws Exception {
        mockMvc.perform(post("/pacientes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(psicologo("dona.pacientes@example.com")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"nome":"Helena Vaz","telefone":"abc"}
                         """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("telefone")));
    }

    @Test
    @DisplayName("cadastro minimo valido continua respondendo 201")
    void cadastroMinimoValidoResponde201() throws Exception {
        mockMvc.perform(post("/pacientes")
                .header(HttpHeaders.AUTHORIZATION, bearerDe(psicologo("dona.pacientes@example.com")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"nome":"Helena Vaz","telefone":"(51) 99811-2345","email":"helena.vaz@example.com"}
                         """))
               .andExpect(status().isCreated());
    }

    // A metade simetrica: as mesmas quatro regras, agora pelo PUT parcial.
    // Prova que @Valid continua no metodo de atualizacao e que as anotacoes
    // valem nos dois DTOs, nao so no cadastro.

    @Test
    @DisplayName("PUT com e-mail de paciente em formato invalido responde 400 (spec 6.1)")
    void putComEmailInvalidoResponde400() throws Exception {
        Usuario dono = psicologo("dona.pacientes.put@example.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Helena Vaz"));

        mockMvc.perform(put("/pacientes/" + paciente.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"email":"helena-arroba-nada"}
                         """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("email")));
    }

    @Test
    @DisplayName("PUT com telefone fora do formato brasileiro responde 400 (spec 6.3)")
    void putComTelefoneForaDoFormatoResponde400() throws Exception {
        Usuario dono = psicologo("dona.pacientes.put@example.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Helena Vaz"));

        mockMvc.perform(put("/pacientes/" + paciente.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"telefone":"abc"}
                         """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("telefone")));
    }

    @Test
    @DisplayName("PUT com nascimento acima de 120 anos responde 400 (spec 6.3)")
    void putComIdadeAcimaDe120Responde400() throws Exception {
        Usuario dono = psicologo("dona.pacientes.put@example.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Helena Vaz"));
        String muitoAntiga = java.time.LocalDate.now().minusYears(121).toString();

        mockMvc.perform(put("/pacientes/" + paciente.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dataNascimento\":\"" + muitoAntiga + "\"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("dataNascimento")));
    }

    @Test
    @DisplayName("PUT com nome de uma letra so responde 400: minimo e 2 tambem na atualizacao (spec 6.3)")
    void putComNomeCurtoDemaisResponde400() throws Exception {
        Usuario dono = psicologo("dona.pacientes.put@example.com");
        Paciente paciente = pacienteRepository.save(Fixtures.paciente(dono, "Helena Vaz"));

        mockMvc.perform(put("/pacientes/" + paciente.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerDe(dono))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"nome":"H"}
                         """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.erros[*].campo").value(hasItem("nome")));
    }
}
