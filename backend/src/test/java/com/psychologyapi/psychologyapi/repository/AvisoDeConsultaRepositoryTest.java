package com.psychologyapi.psychologyapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.psychologyapi.psychologyapi.model.AvisoDeConsulta;
import com.psychologyapi.psychologyapi.model.CanalAviso;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Destinatario;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.support.PostgresContainerConfig;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class AvisoDeConsultaRepositoryTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private ConsultaRepository consultaRepository;

    @Autowired
    private AvisoDeConsultaRepository repository;

    private Consulta consultaSalva(Status status, LocalDateTime deletedAt) {
        Usuario usuario = new Usuario();
        usuario.setNomeUsuario("Marina Aguiar");
        usuario.setEmailUsuario("marina.aguiar." + java.util.UUID.randomUUID() + "@example.com");
        usuario.setSenhaUsuario("hash-irrelevante-para-este-teste");
        usuario = usuarioRepository.save(usuario);

        Paciente paciente = new Paciente();
        paciente.setUsuario(usuario);
        paciente.setNomePaciente("Helena Vaz");
        paciente = pacienteRepository.save(paciente);

        Consulta consulta = new Consulta();
        consulta.setUsuario(usuario);
        consulta.setPaciente(paciente);
        consulta.setDtConsulta(LocalDateTime.now().plusDays(1));
        consulta.setStatus(status);
        consulta.setDeletedAt(deletedAt);
        return consultaRepository.save(consulta);
    }

    private AvisoDeConsulta avisoPendenteParaConsulta(Consulta consulta) {
        AvisoDeConsulta aviso = new AvisoDeConsulta();
        aviso.setConsulta(consulta);
        aviso.setDestinatario(Destinatario.PACIENTE);
        aviso.setCanal(CanalAviso.EMAIL);
        aviso.setAgendadoPara(LocalDateTime.now().minusMinutes(1)); // ja vencido
        return repository.save(aviso);
    }

    private AvisoDeConsulta avisoPendenteSalvo() {
        return avisoPendenteParaConsulta(consultaSalva(Status.AGENDADA, null));
    }

    /**
     * ATENCAO ao que este teste prova e ao que ele NAO prova.
     *
     * {@code @DataJpaTest} e transacional por padrao: as duas chamadas de
     * {@code reivindicar} abaixo rodam na MESMA transacao, entao isto so
     * comprova que uma segunda chamada sequencial, apos a primeira ja ter
     * marcado o aviso como ENVIADO, deixa de bater no WHERE (estado =
     * PENDENTE) e devolve 0. Isso NAO e o mesmo que provar que duas
     * instancias concorrentes (duas transacoes, cada uma correndo pra
     * reivindicar o mesmo aviso ao mesmo tempo) nao ganhariam as duas.
     *
     * A protecao real contra essa concorrencia vem do proprio Postgres: sob
     * READ COMMITTED, um UPDATE que colide em linha ja travada por outra
     * transacao em andamento espera o lock, e ao ganha-lo REAVALIA o WHERE
     * contra o valor ja commitado (que a essa altura nao e mais PENDENTE) —
     * entao a segunda transacao afeta 0 linhas, exatamente como a segunda
     * chamada sequencial aqui. Esta suite NAO tem um teste de duas conexoes
     * concorrentes cobrindo esse caminho; o que garante o comportamento e a
     * semantica do banco, nao um teste desta classe.
     */
    @Test
    @DisplayName("reivindicar duas vezes na mesma transacao: a segunda chamada sequencial nao vence mais (nao cobre concorrencia real entre duas transacoes)")
    void reivindicarNaoVenceDuasVezesNaMesmaTransacao() {
        AvisoDeConsulta aviso = avisoPendenteSalvo();

        int primeira = repository.reivindicar(aviso.getId(), LocalDateTime.now());
        int segunda  = repository.reivindicar(aviso.getId(), LocalDateTime.now());

        assertThat(primeira).isEqualTo(1);
        assertThat(segunda).isZero();
    }

    @Test
    @DisplayName("aVencer traz o que ja passou da hora e ignora o que nao chegou")
    void aVencerRespeitaAHora() {
        avisoPendenteSalvo(); // agendado para um minuto atras

        assertThat(repository.aVencer(LocalDateTime.now())).hasSize(1);
        assertThat(repository.aVencer(LocalDateTime.now().minusHours(1))).isEmpty();
    }

    /**
     * I2 do laudo da revisao do conjunto (2026-09-05): cancelar a consulta
     * nao move o estado do aviso — sem este filtro, aVencer devolveria o
     * aviso e o paciente receberia lembrete de uma sessao cancelada.
     */
    @Test
    @DisplayName("aVencer ignora aviso de consulta cancelada")
    void aVencerIgnoraConsultaCancelada() {
        avisoPendenteParaConsulta(consultaSalva(Status.CANCELADA, null));

        assertThat(repository.aVencer(LocalDateTime.now())).isEmpty();
    }

    /**
     * Mesma logica de I2, para o outro caminho de "a consulta nao vai mais
     * acontecer": exclusao (soft delete), nao so cancelamento explicito.
     */
    @Test
    @DisplayName("aVencer ignora aviso de consulta excluida (soft delete)")
    void aVencerIgnoraConsultaExcluida() {
        avisoPendenteParaConsulta(consultaSalva(Status.AGENDADA, LocalDateTime.now()));

        assertThat(repository.aVencer(LocalDateTime.now())).isEmpty();
    }
}
