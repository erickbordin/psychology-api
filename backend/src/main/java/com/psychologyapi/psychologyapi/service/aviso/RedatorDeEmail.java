package com.psychologyapi.psychologyapi.service.aviso;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.psychologyapi.psychologyapi.model.BlocoAviso;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;

@Component
public class RedatorDeEmail {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATA_POR_EXTENSO = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT_BR);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final String ASSUNTO_LEMBRETE = "Lembrete de agendamento";
    private static final String ASSUNTO_RESUMO = "Sua agenda de amanhã";
    private static final String REMETENTE_RESUMO = "Consultório";

    private final String remetente;
    private final String urlDoFrontend;

    public RedatorDeEmail(@Value("${app.email.remetente}") String remetente,
                          @Value("${app.frontend.url}") String urlDoFrontend) {
        this.remetente = remetente;
        this.urlDoFrontend = urlDoFrontend;
    }

    public Mensagem lembrete(Consulta consulta) {
        Paciente paciente = consulta.getPaciente();
        Usuario usuario = consulta.getUsuario();
        List<String> linhas = new ArrayList<>();
        linhas.add("Olá, " + primeiroNome(paciente.getNomePaciente()) + ".");
        linhas.add("");
        linhas.add("Você tem um horário marcado para " + DATA_POR_EXTENSO.format(consulta.getDtConsulta())
                + ", às " + HORA.format(consulta.getDtConsulta()) + ".");
        linhas.add("");
        boolean temBloco = false;
        if (usuario.getBlocosAviso().contains(BlocoAviso.REMARCAR)) {
            linhas.add("Se precisar remarcar, é só responder este e-mail.");
            temBloco = true;
        }
        if (usuario.getBlocosAviso().contains(BlocoAviso.CHEGADA)) {
            linhas.add("Chegue com alguns minutos de antecedência.");
            temBloco = true;
        }
        if (usuario.getBlocosAviso().contains(BlocoAviso.DURACAO)) {
            linhas.add("Nosso encontro dura 50 minutos.");
            temBloco = true;
        }
        if (temBloco) {
            linhas.add("");
        }
        linhas.add("Não quer mais receber estes lembretes? Acesse:");
        linhas.add(urlDoFrontend + "/descadastro/" + paciente.getTokenDescadastro());
        return new Mensagem(remetente, nomeDoRemetente(usuario), paciente.getEmailPaciente(),
                usuario.getEmailUsuario(), ASSUNTO_LEMBRETE, String.join("\n", linhas));
    }

    public Mensagem resumo(Usuario usuario, LocalDate dia, List<Consulta> sessoes) {
        List<String> linhas = new ArrayList<>();
        linhas.add("Olá, " + primeiroNome(usuario.getNomeUsuario()) + ".");
        linhas.add("");
        linhas.add("Amanhã, " + DATA_POR_EXTENSO.format(dia) + ", você tem " + sessoes.size() + " "
                + (sessoes.size() == 1 ? "sessão" : "sessões") + ":");
        linhas.add("");
        for (Consulta sessao : sessoes) {
            linhas.add(HORA.format(sessao.getDtConsulta()) + " · " + sessao.getPaciente().getNomePaciente());
        }
        linhas.add("");
        linhas.add("Para deixar de receber este resumo, desligue-o em Ajustes.");
        return new Mensagem(remetente, REMETENTE_RESUMO, usuario.getEmailUsuario(), null,
                ASSUNTO_RESUMO, String.join("\n", linhas));
    }

    private String nomeDoRemetente(Usuario usuario) {
        String titulo = usuario.getTituloProfissional();
        if (usuario.isTituloNoEmail() && titulo != null && !titulo.isBlank()) {
            return usuario.getNomeUsuario() + " · " + titulo.trim();
        }
        return usuario.getNomeUsuario();
    }

    private String primeiroNome(String nome) {
        return nome.trim().split("\\s+")[0];
    }
}
