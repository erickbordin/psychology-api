package com.psychologyapi.psychologyapi.exceptions;

/**
 * Spec 5.4: o canal de aviso EMAIL exige e-mail do paciente.
 *
 * No CADASTRO essa regra e de bean ({@code @EmailExigidoPeloCanal} sobre
 * {@code PacienteCadastroDTO}), e ali ler so o payload esta certo: o payload e
 * o estado inteiro do paciente que esta nascendo.
 *
 * Na ATUALIZACAO nao da: o PUT e parcial, entao um validador que so ve o corpo
 * recusava {@code {"canalAviso":"EMAIL"}} sozinho mesmo quando o paciente ja
 * tinha e-mail salvo — 400 falso num caminho legitimo. A regra passou para o
 * {@code PacienteService}, sobre o estado EFETIVO (payload sobre entidade), e
 * chega ate o cliente por esta excecao.
 *
 * O {@code GlobalExceptionsHandler} a devolve no MESMO 400 com campo "email"
 * que a validacao de bean produzia, para o cliente nao ter de distinguir a
 * origem da recusa.
 */
public class EmailExigidoPeloCanalException extends RuntimeException {

    public EmailExigidoPeloCanalException(String mensagem) {
        super(mensagem);
    }
}
