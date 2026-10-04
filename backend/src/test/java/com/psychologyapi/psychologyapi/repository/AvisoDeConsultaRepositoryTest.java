package com.psychologyapi.psychologyapi.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
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
    private TestEntityManager em;

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

    private AvisoDeConsulta avisoNovo(Consulta consulta, LocalDateTime dataHoraConsulta) {
        AvisoDeConsulta aviso = new AvisoDeConsulta();
        aviso.setConsulta(consulta);
        aviso.setDestinatario(Destinatario.PACIENTE);
        aviso.setCanal(CanalAviso.EMAIL);
        aviso.setAgendadoPara(dataHoraConsulta.minusDays(1));
        aviso.setDataHoraConsulta(dataHoraConsulta);
        return aviso;
    }

    @Test
    @DisplayName("dois avisos para a mesma consulta, destinatario e horario violam o indice unico")
    void avisoDuplicadoViolaIndiceUnico() {
        Consulta consulta = consultaSalva(Status.AGENDADA, null);
        LocalDateTime horario = LocalDateTime.now().plusDays(1);
        em.persistAndFlush(avisoNovo(consulta, horario));

        assertThatThrownBy(() -> em.persistAndFlush(avisoNovo(consulta, horario)))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("uq_aviso_consulta_destinatario_horario");
    }

    @Test
    @DisplayName("o mesmo aviso com horario diferente e permitido (remarcacao)")
    void mesmoAvisoComHorarioDiferenteEPermitido() {
        Consulta consulta = consultaSalva(Status.AGENDADA, null);
        LocalDateTime horario = LocalDateTime.now().plusDays(1);
        em.persistAndFlush(avisoNovo(consulta, horario));
        em.persistAndFlush(avisoNovo(consulta, horario.plusHours(2)));

        assertThat(repository.count()).isEqualTo(2);
    }
}
