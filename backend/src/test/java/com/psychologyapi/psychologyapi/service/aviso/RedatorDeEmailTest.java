package com.psychologyapi.psychologyapi.service.aviso;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.psychologyapi.psychologyapi.model.BlocoAviso;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.support.Fixtures;

class RedatorDeEmailTest {

    private final RedatorDeEmail redator = new RedatorDeEmail("lembretes@exemplo.local", "http://app.local");

    private Usuario psicologo(Set<BlocoAviso> blocos, boolean titulo, String tituloProfissional) {
        Usuario usuario = Fixtures.usuario("ana@exemplo.local");
        usuario.setNomeUsuario("Ana Souza");
        usuario.setBlocosAviso(blocos);
        usuario.setTituloNoEmail(titulo);
        usuario.setTituloProfissional(tituloProfissional);
        return usuario;
    }

    private Consulta sessao(Usuario usuario, String nome, LocalDateTime quando) {
        Paciente paciente = Fixtures.paciente(usuario, nome);
        paciente.setEmailPaciente("helena@exemplo.local");
        paciente.setTokenDescadastro("tok-123");
        return Fixtures.consulta(usuario, paciente, quando, Status.AGENDADA);
    }

    private static final LocalDateTime QUINTA = LocalDateTime.of(2026, 10, 8, 14, 0);

    @Test
    void lembreteComBlocosRemarcarEDuracao() {
        Usuario usuario = psicologo(EnumSet.of(BlocoAviso.REMARCAR, BlocoAviso.DURACAO), false, null);

        Mensagem mensagem = redator.lembrete(sessao(usuario, "Helena Vaz", QUINTA));

        assertThat(mensagem.assunto()).isEqualTo("Lembrete de agendamento");
        assertThat(mensagem.de()).isEqualTo("lembretes@exemplo.local");
        assertThat(mensagem.nomeDoRemetente()).isEqualTo("Ana Souza");
        assertThat(mensagem.responderPara()).isEqualTo("ana@exemplo.local");
        assertThat(mensagem.para()).isEqualTo("helena@exemplo.local");
        assertThat(mensagem.corpo()).isEqualTo(String.join("\n",
                "Olá, Helena.",
                "",
                "Você tem um horário marcado para quinta-feira, 8 de outubro, às 14:00.",
                "",
                "Se precisar remarcar, é só responder este e-mail.",
                "Nosso encontro dura 50 minutos.",
                "",
                "Não quer mais receber estes lembretes? Acesse:",
                "http://app.local/descadastro/tok-123"));
    }

    @Test
    void lembreteComTodosOsBlocosNaOrdem() {
        Usuario usuario = psicologo(EnumSet.allOf(BlocoAviso.class), false, null);

        Mensagem mensagem = redator.lembrete(sessao(usuario, "Helena Vaz", QUINTA));

        assertThat(mensagem.corpo()).contains(String.join("\n",
                "Se precisar remarcar, é só responder este e-mail.",
                "Chegue com alguns minutos de antecedência.",
                "Nosso encontro dura 50 minutos."));
    }

    @Test
    void tituloLigadoAcrescentaOTituloAoNomeDoRemetente() {
        Usuario usuario = psicologo(EnumSet.noneOf(BlocoAviso.class), true, "Psicóloga · CRP 07/12345");

        Mensagem mensagem = redator.lembrete(sessao(usuario, "Helena Vaz", QUINTA));

        assertThat(mensagem.nomeDoRemetente()).isEqualTo("Ana Souza · Psicóloga · CRP 07/12345");
    }

    @Test
    void tituloLigadoMasEmBrancoUsaSoONome() {
        Usuario usuario = psicologo(EnumSet.noneOf(BlocoAviso.class), true, "   ");

        Mensagem mensagem = redator.lembrete(sessao(usuario, "Helena Vaz", QUINTA));

        assertThat(mensagem.nomeDoRemetente()).isEqualTo("Ana Souza");
    }

    @Test
    void semBlocosHaUmaSoLinhaEmBrancoEntreOHorarioEODescadastro() {
        Usuario usuario = psicologo(EnumSet.noneOf(BlocoAviso.class), false, null);

        Mensagem mensagem = redator.lembrete(sessao(usuario, "Helena Vaz", QUINTA));

        assertThat(mensagem.corpo()).isEqualTo(String.join("\n",
                "Olá, Helena.",
                "",
                "Você tem um horário marcado para quinta-feira, 8 de outubro, às 14:00.",
                "",
                "Não quer mais receber estes lembretes? Acesse:",
                "http://app.local/descadastro/tok-123"));
    }

    @Test
    void corpoEAssuntoNaoTemTermoClinicoComOTituloDesligado() {
        Usuario usuario = psicologo(EnumSet.allOf(BlocoAviso.class), false, "Psicóloga · CRP 07/12345");

        Mensagem mensagem = redator.lembrete(sessao(usuario, "Helena Vaz", QUINTA));

        String texto = (mensagem.assunto() + "\n" + mensagem.corpo()).toLowerCase();
        assertThat(texto).doesNotContain("psicólog", "terapia", "sessão", "consulta");
    }

    @Test
    void resumoDeDoisPacientes() {
        Usuario usuario = psicologo(EnumSet.noneOf(BlocoAviso.class), false, null);
        Consulta manha = sessao(usuario, "Helena Vaz", LocalDateTime.of(2026, 10, 8, 9, 0));
        Consulta tarde = sessao(usuario, "Rui Sampaio", LocalDateTime.of(2026, 10, 8, 14, 30));

        Mensagem mensagem = redator.resumo(usuario, LocalDate.of(2026, 10, 8), List.of(manha, tarde));

        assertThat(mensagem.assunto()).isEqualTo("Sua agenda de amanhã");
        assertThat(mensagem.de()).isEqualTo("lembretes@exemplo.local");
        assertThat(mensagem.nomeDoRemetente()).isEqualTo("Consultório");
        assertThat(mensagem.para()).isEqualTo("ana@exemplo.local");
        assertThat(mensagem.responderPara()).isNull();
        assertThat(mensagem.corpo()).isEqualTo(String.join("\n",
                "Olá, Ana.",
                "",
                "Amanhã, quinta-feira, 8 de outubro, você tem 2 sessões:",
                "",
                "09:00 · Helena Vaz",
                "14:30 · Rui Sampaio",
                "",
                "Para deixar de receber este resumo, desligue-o em Ajustes."));
    }

    @Test
    void resumoDeUmaSessaoUsaOSingular() {
        Usuario usuario = psicologo(EnumSet.noneOf(BlocoAviso.class), false, null);
        Consulta unica = sessao(usuario, "Helena Vaz", LocalDateTime.of(2026, 10, 8, 9, 0));

        Mensagem mensagem = redator.resumo(usuario, LocalDate.of(2026, 10, 8), List.of(unica));

        assertThat(mensagem.corpo()).contains("você tem 1 sessão:");
    }
}
