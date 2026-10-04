package com.psychologyapi.psychologyapi.service.aviso;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.psychologyapi.psychologyapi.model.Antecedencia;
import com.psychologyapi.psychologyapi.model.AvisoDeConsulta;
import com.psychologyapi.psychologyapi.model.CanalAviso;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.EstadoAviso;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.ResumoDiario;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.AvisoDeConsultaRepository;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.ResumoDiarioRepository;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.support.CarteiroDeTeste;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.HttpTest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@HttpTest
class ServicoDeAvisosTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 12, 2, 10, 0);
    private static final LocalDateTime AMANHA_9H = LocalDateTime.of(2026, 12, 3, 9, 0);

    @Autowired
    private ServicoDeAvisos servico;

    @Autowired
    private CarteiroDeTeste.Falso carteiro;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private ConsultaRepository consultaRepository;

    @Autowired
    private AvisoDeConsultaRepository avisoRepository;

    @Autowired
    private ResumoDiarioRepository resumoRepository;

    @PersistenceContext
    private EntityManager em;

    private Usuario usuario;
    private Paciente paciente;
    private String emailDoPaciente;
    private String emailDoPsicologo;

    @BeforeEach
    void preparar() {
        carteiro.limpar();
        emailDoPsicologo = "psi." + UUID.randomUUID() + "@exemplo.local";
        emailDoPaciente = "pac." + UUID.randomUUID() + "@exemplo.local";
        usuario = Fixtures.usuario(emailDoPsicologo);
        usuario.setNomeUsuario("Ana Souza");
        usuario.setAntecedencia(Antecedencia.H24);
        usuario = usuarioRepository.save(usuario);
        paciente = pacienteAutorizado("Cadastro A");
    }

    private Paciente pacienteAutorizado(String nome) {
        Paciente novo = Fixtures.paciente(usuario, nome);
        novo.setEmailPaciente(emailDoPaciente);
        novo.setCanalAviso(CanalAviso.EMAIL);
        novo.setConsentimentoAvisoEm(AGORA.minusDays(30));
        return pacienteRepository.save(novo);
    }

    private Consulta sessaoEm(LocalDateTime quando) {
        return consultaRepository.save(Fixtures.consulta(usuario, paciente, quando, Status.AGENDADA));
    }

    private void passada(LocalDateTime agora) {
        servico.passada(agora);
        em.flush();
        em.clear();
    }

    private List<AvisoDeConsulta> avisosDe(Consulta consulta) {
        em.flush();
        em.clear();
        return avisoRepository.findAll().stream()
                .filter(a -> a.getConsulta().getId().equals(consulta.getId()))
                .toList();
    }

    private List<ResumoDiario> resumosDoPsicologo() {
        em.flush();
        em.clear();
        return resumoRepository.findAll().stream()
                .filter(r -> r.getUsuario().getId().equals(usuario.getId()))
                .toList();
    }

    private ResumoDiario unicoResumo() {
        List<ResumoDiario> resumos = resumosDoPsicologo();
        assertThat(resumos).hasSize(1);
        return resumos.get(0);
    }

    private void sessoesAmanhaParaResumo() {
        Paciente outro = pacienteRepository.save(Fixtures.paciente(usuario, "Cadastro B"));
        consultaRepository.save(Fixtures.consulta(usuario, outro, AMANHA_9H, Status.AGENDADA));
    }

    private AvisoDeConsulta unicoAvisoDe(Consulta consulta) {
        List<AvisoDeConsulta> avisos = avisosDe(consulta);
        assertThat(avisos).hasSize(1);
        return avisos.get(0);
    }

    private List<Mensagem> lembretesEnviados() {
        return carteiro.enviadas().stream()
                .filter(m -> emailDoPaciente.equals(m.para()))
                .toList();
    }

    private List<Mensagem> resumosEnviados() {
        return carteiro.enviadas().stream()
                .filter(m -> emailDoPsicologo.equals(m.para()))
                .toList();
    }

    private void alterarPaciente(Consumer<Paciente> mudanca) {
        em.flush();
        em.clear();
        Paciente relido = pacienteRepository.findById(paciente.getId()).orElseThrow();
        mudanca.accept(relido);
        pacienteRepository.saveAndFlush(relido);
        em.clear();
    }

    private void alterarConsulta(Consulta consulta, Consumer<Consulta> mudanca) {
        em.flush();
        em.clear();
        Consulta relida = consultaRepository.findById(consulta.getId()).orElseThrow();
        mudanca.accept(relida);
        consultaRepository.saveAndFlush(relida);
        em.clear();
    }

    private void alterarUsuario(Consumer<Usuario> mudanca) {
        em.flush();
        em.clear();
        Usuario relido = usuarioRepository.findById(usuario.getId()).orElseThrow();
        mudanca.accept(relido);
        usuarioRepository.saveAndFlush(relido);
        em.clear();
    }

    @Test
    @DisplayName("sessão dentro de 24h envia um e-mail e fecha o aviso como enviado")
    void envia_sessaoDentroDe24h() {
        Consulta consulta = sessaoEm(AMANHA_9H);

        passada(AGORA);

        assertThat(lembretesEnviados()).hasSize(1);
        AvisoDeConsulta aviso = unicoAvisoDe(consulta);
        assertThat(aviso.getEstado()).isEqualTo(EstadoAviso.ENVIADO);
        assertThat(aviso.getTentativas()).isEqualTo(1);
        assertThat(aviso.getDataHoraConsulta()).isEqualTo(AMANHA_9H);
        assertThat(aviso.getEnviadoEm()).isEqualTo(AGORA);
    }

    @Test
    @DisplayName("duas passadas seguidas mantêm um só e-mail e uma só linha")
    void duasPassadas_naoDuplicam() {
        Consulta consulta = sessaoEm(AMANHA_9H);

        passada(AGORA);
        passada(AGORA);

        assertThat(lembretesEnviados()).hasSize(1);
        assertThat(avisosDe(consulta)).hasSize(1);
    }

    @Test
    @DisplayName("sessão fora da janela de 24h não envia nada")
    void foraDaJanela_naoEnvia() {
        Consulta consulta = sessaoEm(LocalDateTime.of(2026, 12, 4, 9, 0));

        passada(AGORA);

        assertThat(lembretesEnviados()).isEmpty();
        assertThat(avisosDe(consulta)).isEmpty();
    }

    @Test
    @DisplayName("antecedência de 2h só envia quando chega o momento")
    void antecedenciaH2() {
        alterarUsuario(u -> u.setAntecedencia(Antecedencia.H2));
        sessaoEm(LocalDateTime.of(2026, 12, 2, 15, 0));

        passada(AGORA);
        assertThat(lembretesEnviados()).isEmpty();

        passada(LocalDateTime.of(2026, 12, 2, 13, 0));
        assertThat(lembretesEnviados()).hasSize(1);
    }

    @Test
    @DisplayName("antecedência na véspera só envia a partir das 18h do dia anterior")
    void antecedenciaVespera() {
        alterarUsuario(u -> u.setAntecedencia(Antecedencia.VESPERA));
        sessaoEm(LocalDateTime.of(2026, 12, 3, 8, 0));

        passada(AGORA);
        assertThat(lembretesEnviados()).isEmpty();

        passada(LocalDateTime.of(2026, 12, 2, 18, 0));
        assertThat(lembretesEnviados()).hasSize(1);
    }

    @Test
    @DisplayName("sessão criada já dentro da janela envia logo")
    void sessaoJaDentroDaJanela_enviaLogo() {
        sessaoEm(LocalDateTime.of(2026, 12, 2, 12, 0));

        passada(AGORA);

        assertThat(lembretesEnviados()).hasSize(1);
    }

    @Test
    @DisplayName("paciente sem consentimento não recebe aviso")
    void semConsentimento_naoEnvia() {
        Consulta consulta = sessaoEm(AMANHA_9H);
        alterarPaciente(p -> p.setConsentimentoAvisoEm(null));

        passada(AGORA);

        assertThat(lembretesEnviados()).isEmpty();
        assertThat(avisosDe(consulta)).isEmpty();
    }

    @Test
    @DisplayName("paciente com canal nenhum não recebe aviso")
    void canalNenhum_naoEnvia() {
        Consulta consulta = sessaoEm(AMANHA_9H);
        alterarPaciente(p -> p.setCanalAviso(CanalAviso.NENHUM));

        passada(AGORA);

        assertThat(lembretesEnviados()).isEmpty();
        assertThat(avisosDe(consulta)).isEmpty();
    }

    @Test
    @DisplayName("paciente sem e-mail não recebe aviso")
    void semEmail_naoEnvia() {
        Consulta consulta = sessaoEm(AMANHA_9H);
        alterarPaciente(p -> p.setEmailPaciente(null));

        passada(AGORA);

        assertThat(carteiro.enviadas()).noneMatch(m -> emailDoPaciente.equals(m.para()));
        assertThat(avisosDe(consulta)).isEmpty();
    }

    @Test
    @DisplayName("paciente excluído não recebe aviso")
    void pacienteExcluido_naoEnvia() {
        Consulta consulta = sessaoEm(AMANHA_9H);
        alterarPaciente(p -> p.setDeletedAt(AGORA.minusDays(1)));

        passada(AGORA);

        assertThat(lembretesEnviados()).isEmpty();
        assertThat(avisosDe(consulta)).isEmpty();
    }

    @Test
    @DisplayName("sessão cancelada não gera aviso")
    void sessaoCancelada_naoEnvia() {
        Consulta consulta = sessaoEm(AMANHA_9H);
        alterarConsulta(consulta, c -> c.setStatus(Status.CANCELADA));

        passada(AGORA);

        assertThat(lembretesEnviados()).isEmpty();
        assertThat(avisosDe(consulta)).isEmpty();
    }

    @Test
    @DisplayName("sessão excluída não gera aviso")
    void sessaoExcluida_naoEnvia() {
        Consulta consulta = sessaoEm(AMANHA_9H);
        alterarConsulta(consulta, c -> c.setDeletedAt(AGORA.minusHours(1)));

        passada(AGORA);

        assertThat(lembretesEnviados()).isEmpty();
        assertThat(avisosDe(consulta)).isEmpty();
    }

    @Test
    @DisplayName("remarcar a sessão gera um segundo aviso para o novo horário")
    void remarcacao_geraNovoAviso() {
        Consulta consulta = sessaoEm(AMANHA_9H);
        passada(AGORA);
        assertThat(lembretesEnviados()).hasSize(1);

        LocalDateTime novoHorario = LocalDateTime.of(2026, 12, 3, 11, 0);
        alterarConsulta(consulta, c -> c.setDtConsulta(novoHorario));
        passada(AGORA.plusHours(1));

        assertThat(lembretesEnviados()).hasSize(2);
        List<AvisoDeConsulta> avisos = avisosDe(consulta);
        assertThat(avisos).hasSize(2);
        assertThat(avisos).extracting(AvisoDeConsulta::getDataHoraConsulta)
                .containsExactlyInAnyOrder(AMANHA_9H, novoHorario);
    }

    @Test
    @DisplayName("primeira falha deixa o aviso pendente com nova tentativa em 5 minutos")
    void primeiraFalha_agendaNovaTentativa() {
        Consulta consulta = sessaoEm(AMANHA_9H);
        carteiro.falharNasProximas(1);

        passada(AGORA);

        AvisoDeConsulta aviso = unicoAvisoDe(consulta);
        assertThat(aviso.getEstado()).isEqualTo(EstadoAviso.PENDENTE);
        assertThat(aviso.getTentativas()).isEqualTo(1);
        assertThat(aviso.getProximaTentativa()).isEqualTo(AGORA.plusMinutes(5));
        assertThat(aviso.getErro()).isEqualTo("MailSendException");

        passada(AGORA.plusMinutes(5));

        AvisoDeConsulta depois = unicoAvisoDe(consulta);
        assertThat(depois.getEstado()).isEqualTo(EstadoAviso.ENVIADO);
        assertThat(lembretesEnviados()).hasSize(1);
    }

    @Test
    @DisplayName("três falhas fecham o aviso como falhou sem enviar nada")
    void tresFalhas_falhou() {
        Consulta consulta = sessaoEm(AMANHA_9H);
        carteiro.falharNasProximas(3);

        passada(AGORA);
        passada(AGORA.plusMinutes(4));
        assertThat(unicoAvisoDe(consulta).getTentativas()).isEqualTo(1);
        passada(AGORA.plusMinutes(5));

        AvisoDeConsulta segunda = unicoAvisoDe(consulta);
        assertThat(segunda.getEstado()).isEqualTo(EstadoAviso.PENDENTE);
        assertThat(segunda.getTentativas()).isEqualTo(2);
        assertThat(segunda.getProximaTentativa()).isEqualTo(AGORA.plusMinutes(20));

        passada(AGORA.plusMinutes(19));
        assertThat(unicoAvisoDe(consulta).getTentativas()).isEqualTo(2);

        passada(AGORA.plusMinutes(20));

        AvisoDeConsulta aviso = unicoAvisoDe(consulta);
        assertThat(aviso.getEstado()).isEqualTo(EstadoAviso.FALHOU);
        assertThat(aviso.getTentativas()).isEqualTo(3);
        assertThat(lembretesEnviados()).isEmpty();
    }

    @Test
    @DisplayName("consentimento revogado depois da falha cancela o aviso sem enviar")
    void revogacaoNoMeio_cancela() {
        Consulta consulta = sessaoEm(AMANHA_9H);
        carteiro.falharNasProximas(1);
        passada(AGORA);

        alterarPaciente(p -> {
            p.setCanalAviso(CanalAviso.NENHUM);
            p.setConsentimentoAvisoEm(null);
        });
        passada(AGORA.plusMinutes(5));

        assertThat(unicoAvisoDe(consulta).getEstado()).isEqualTo(EstadoAviso.CANCELADO);
        assertThat(lembretesEnviados()).isEmpty();
    }

    @Test
    @DisplayName("remarcação depois da falha cancela o aviso velho e envia o do novo horário")
    void remarcacaoNoMeio_cancelaVelhoEEnviaNovo() {
        Consulta consulta = sessaoEm(AMANHA_9H);
        carteiro.falharNasProximas(1);
        passada(AGORA);

        LocalDateTime novoHorario = LocalDateTime.of(2026, 12, 3, 10, 0);
        alterarConsulta(consulta, c -> c.setDtConsulta(novoHorario));
        passada(AGORA.plusMinutes(5));

        List<AvisoDeConsulta> avisos = avisosDe(consulta);
        assertThat(avisos).hasSize(2);
        AvisoDeConsulta velho = avisos.stream()
                .filter(a -> a.getDataHoraConsulta().equals(AMANHA_9H)).findFirst().orElseThrow();
        AvisoDeConsulta novo = avisos.stream()
                .filter(a -> a.getDataHoraConsulta().equals(novoHorario)).findFirst().orElseThrow();
        assertThat(velho.getEstado()).isEqualTo(EstadoAviso.CANCELADO);
        assertThat(novo.getEstado()).isEqualTo(EstadoAviso.ENVIADO);
        assertThat(lembretesEnviados()).hasSize(1);
    }

    @Test
    @DisplayName("resumo diário só sai a partir das 18h e não se repete")
    void resumo_saiAs18hUmaVez() {
        Paciente outro = pacienteRepository.save(Fixtures.paciente(usuario, "Cadastro B"));
        consultaRepository.save(Fixtures.consulta(usuario, outro, LocalDateTime.of(2026, 12, 3, 9, 0), Status.AGENDADA));
        consultaRepository.save(Fixtures.consulta(usuario, outro, LocalDateTime.of(2026, 12, 3, 15, 0), Status.AGENDADA));

        passada(LocalDateTime.of(2026, 12, 2, 17, 59));
        assertThat(resumosEnviados()).isEmpty();

        passada(LocalDateTime.of(2026, 12, 2, 18, 0));
        assertThat(resumosEnviados()).hasSize(1);
        assertThat(resumosEnviados().get(0).assunto()).isEqualTo("Sua agenda de amanhã");

        passada(LocalDateTime.of(2026, 12, 2, 18, 0));
        passada(LocalDateTime.of(2026, 12, 2, 18, 5));
        assertThat(resumosEnviados()).hasSize(1);
    }

    @Test
    @DisplayName("resumo desligado não envia nada")
    void resumoDesligado_naoEnvia() {
        alterarUsuario(u -> u.setResumoDiario(false));
        Paciente outro = pacienteRepository.save(Fixtures.paciente(usuario, "Cadastro B"));
        consultaRepository.save(Fixtures.consulta(usuario, outro, AMANHA_9H, Status.AGENDADA));

        passada(LocalDateTime.of(2026, 12, 2, 18, 0));

        assertThat(resumosEnviados()).isEmpty();
    }

    @Test
    @DisplayName("dia seguinte sem sessão agendada não envia resumo")
    void semSessaoAmanha_naoEnviaResumo() {
        Paciente outro = pacienteRepository.save(Fixtures.paciente(usuario, "Cadastro B"));
        consultaRepository.save(Fixtures.consulta(usuario, outro, LocalDateTime.of(2026, 12, 4, 9, 0), Status.AGENDADA));
        consultaRepository.save(Fixtures.consulta(usuario, outro, AMANHA_9H, Status.CANCELADA));

        passada(LocalDateTime.of(2026, 12, 2, 18, 0));

        assertThat(resumosEnviados()).isEmpty();
    }

    @Test
    @DisplayName("o erro gravado nunca contém o e-mail do paciente")
    void erro_naoVazaEmail() {
        Consulta consulta = sessaoEm(AMANHA_9H);
        carteiro.falharNasProximas(1);

        passada(AGORA);

        String erro = unicoAvisoDe(consulta).getErro();
        assertThat(erro).isNotBlank();
        assertThat(erro).doesNotContain(emailDoPaciente).doesNotContain("@");
    }

    @Test
    @DisplayName("resumo que falhou às 18h passa da meia-noite e é cancelado sem enviar")
    void resumo_canceladoDepoisDaMeiaNoite() {
        sessoesAmanhaParaResumo();
        carteiro.falharNasProximas(1);
        passada(LocalDateTime.of(2026, 12, 2, 18, 0));

        passada(LocalDateTime.of(2026, 12, 3, 0, 5));

        assertThat(unicoResumo().getEstado()).isEqualTo(EstadoAviso.CANCELADO);
        assertThat(resumosEnviados()).isEmpty();
    }

    @Test
    @DisplayName("resumo que falhou tenta de novo em 5 minutos e fecha como enviado")
    void resumo_novaTentativa() {
        sessoesAmanhaParaResumo();
        carteiro.falharNasProximas(1);
        LocalDateTime as18 = LocalDateTime.of(2026, 12, 2, 18, 0);

        passada(as18);

        ResumoDiario pendente = unicoResumo();
        assertThat(pendente.getEstado()).isEqualTo(EstadoAviso.PENDENTE);
        assertThat(pendente.getTentativas()).isEqualTo(1);
        assertThat(pendente.getProximaTentativa()).isEqualTo(as18.plusMinutes(5));

        passada(as18.plusMinutes(5));

        assertThat(unicoResumo().getEstado()).isEqualTo(EstadoAviso.ENVIADO);
        assertThat(resumosEnviados()).hasSize(1);
    }

    @Test
    @DisplayName("resumo com três falhas fecha como falhou")
    void resumo_tresFalhas() {
        sessoesAmanhaParaResumo();
        carteiro.falharNasProximas(3);
        LocalDateTime as18 = LocalDateTime.of(2026, 12, 2, 18, 0);

        passada(as18);
        passada(as18.plusMinutes(5));
        ResumoDiario segunda = unicoResumo();
        assertThat(segunda.getTentativas()).isEqualTo(2);
        assertThat(segunda.getProximaTentativa()).isEqualTo(as18.plusMinutes(20));
        passada(as18.plusMinutes(20));

        ResumoDiario resumo = unicoResumo();
        assertThat(resumo.getEstado()).isEqualTo(EstadoAviso.FALHOU);
        assertThat(resumo.getTentativas()).isEqualTo(3);
        assertThat(resumo.getErro()).isEqualTo("MailSendException");
        assertThat(resumosEnviados()).isEmpty();
    }

    @Test
    @DisplayName("sessão que já começou não gera aviso nem e-mail")
    void sessaoPassada_naoEnvia() {
        Consulta consulta = sessaoEm(AGORA.minusHours(1));

        passada(AGORA);

        assertThat(lembretesEnviados()).isEmpty();
        assertThat(avisosDe(consulta)).isEmpty();
    }

    @Test
    @DisplayName("aviso pendente cuja sessão começou antes da nova tentativa é cancelado sem enviar")
    void retentativa_sessaoJaPassou_cancela() {
        Consulta consulta = sessaoEm(AGORA.plusMinutes(3));
        carteiro.falharNasProximas(1);
        passada(AGORA);

        passada(AGORA.plusMinutes(5));

        assertThat(unicoAvisoDe(consulta).getEstado()).isEqualTo(EstadoAviso.CANCELADO);
        assertThat(lembretesEnviados()).isEmpty();
    }

    @Test
    @DisplayName("sessão além de 31h não gera aviso")
    void alemDe31h_naoRegistra() {
        Consulta consulta = sessaoEm(AGORA.plusHours(31).plusMinutes(1));

        passada(AGORA);

        assertThat(avisosDe(consulta)).isEmpty();
        assertThat(lembretesEnviados()).isEmpty();
    }

    @Test
    @DisplayName("passada repetida no mesmo instante com aviso pendente não duplica nem reenvia")
    void duasPassadas_comPendente() {
        Consulta consulta = sessaoEm(AMANHA_9H);
        carteiro.falharNasProximas(1);

        passada(AGORA);
        passada(AGORA);

        AvisoDeConsulta aviso = unicoAvisoDe(consulta);
        assertThat(aviso.getEstado()).isEqualTo(EstadoAviso.PENDENTE);
        assertThat(aviso.getTentativas()).isEqualTo(1);
        assertThat(lembretesEnviados()).isEmpty();
    }
}
