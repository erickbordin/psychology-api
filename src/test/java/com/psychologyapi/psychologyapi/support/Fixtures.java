package com.psychologyapi.psychologyapi.support;

import java.time.LocalDateTime;

import com.psychologyapi.psychologyapi.model.Anotacao;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Lembrete;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;

/** Entidades minimas validas para os testes. */
public final class Fixtures {

    private Fixtures() {
    }

    public static Usuario usuario(String email) {
        Usuario usuario = new Usuario();
        usuario.setNomeUsuario("Psicologo " + email);
        usuario.setEmailUsuario(email);
        usuario.setSenhaUsuario("$2a$10$hashfalsoparateste");
        return usuario;
    }

    public static Paciente paciente(Usuario dono, String nome) {
        Paciente paciente = new Paciente();
        paciente.setUsuario(dono);
        paciente.setNomePaciente(nome);
        return paciente;
    }

    public static Consulta consulta(Usuario dono, Paciente paciente, LocalDateTime dtConsulta, Status status) {
        Consulta consulta = new Consulta();
        consulta.setUsuario(dono);
        consulta.setPaciente(paciente);
        consulta.setDtConsulta(dtConsulta);
        consulta.setStatus(status);
        return consulta;
    }

    public static Lembrete lembrete(Paciente paciente, String descricao) {
        Lembrete lembrete = new Lembrete();
        lembrete.setPaciente(paciente);
        lembrete.setDescricao(descricao);
        return lembrete;
    }

    public static Anotacao anotacao(Usuario dono, Paciente paciente, String conteudo) {
        Anotacao anotacao = new Anotacao();
        anotacao.setUsuario(dono);
        anotacao.setPaciente(paciente);
        anotacao.setConteudo(conteudo);
        return anotacao;
    }
}
