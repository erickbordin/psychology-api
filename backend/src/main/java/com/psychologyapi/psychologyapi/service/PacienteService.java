package com.psychologyapi.psychologyapi.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.controller.dto.paciente.PacienteAtualizacaoDTO;
import com.psychologyapi.psychologyapi.controller.dto.paciente.PacienteCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.paciente.PacienteResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.EmailExigidoPeloCanalException;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.mapper.PacienteMapper;
import com.psychologyapi.psychologyapi.model.CanalAviso;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PacienteService {

    private final PacienteRepository pacienteRepository;
    private final ConsultaRepository consultaRepository;
    private final PacienteMapper pacienteMapper;
    private final SecurityService securityService;
    private final AuditoriaService auditoriaService;

    @Transactional
    public PacienteResponseDTO cadastrar(PacienteCadastroDTO dto) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteMapper.toEntity(dto);
        paciente.setUsuario(usuarioLogado);
        boolean consentiu = paciente.getCanalAviso() == CanalAviso.EMAIL;
        if (consentiu) {
            paciente.setConsentimentoAvisoEm(LocalDateTime.now());
        }
        Paciente pacienteSalvo = pacienteRepository.save(paciente);
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.CRIACAO, "Paciente", pacienteSalvo.getId());
        if (consentiu) {
            auditoriaService.registrar(usuarioLogado, AcaoAuditoria.ATUALIZACAO, "ConsentimentoDoPaciente",
                    pacienteSalvo.getId());
        }
        return pacienteMapper.toResponseDTO(pacienteSalvo);
    }

    public PacienteResponseDTO buscarPorId(UUID id) {
        Usuario usuario = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, usuario)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));
        auditoriaService.registrar(usuario, AcaoAuditoria.VISUALIZACAO, "Paciente", paciente.getId());
        return pacienteMapper.toResponseDTO(paciente);
    }

    public List<PacienteResponseDTO> listarPacientes(String nome) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        List<Paciente> pacientes = (nome == null || nome.isBlank())
                ? pacienteRepository.findByUsuarioAndDeletedAtIsNull(usuarioLogado)
                : pacienteRepository.findByUsuarioAndNomePacienteContainingIgnoreCaseAndDeletedAtIsNull(usuarioLogado,
                        nome);
        return pacientes.stream()
                .map(pacienteMapper::toResponseDTO)
                .toList();
    }

    /**
     * PUT /pacientes/{id} — atualizacao parcial.
     *
     * <h2>A convencao de limpeza (decisao do dono, revisao da Fatia 2)</h2>
     *
     * Campo <b>ausente</b> do corpo nao e tocado — garantia do
     * {@code NullValuePropertyMappingStrategy.IGNORE} no mapper, que conserta a
     * divida I6. Campo enviado <b>em branco</b> ({@code ""}) limpa o que estava
     * salvo, e so para {@code email} e {@code telefone}.
     *
     * A conversao branco -&gt; nulo mora AQUI, e nao no mapper, de proposito:
     * o mapper e quem carrega a estrategia IGNORE, e e exatamente ela que a
     * regra de "ausente nao mexe" depende. Mexer no mapper para expressar a
     * limpeza traria a I6 de volta pela porta dos fundos.
     *
     * <h2>A regra condicional de spec 5.4</h2>
     *
     * Ela e avaliada sobre o estado EFETIVO — payload sobre entidade — e
     * ANTES de qualquer mutacao. Antes, e nao depois: uma recusa depois da
     * mutacao dependeria do rollback da transacao para nao deixar meio-estado,
     * e depender de rollback para correcao e apostar em algo que a anotacao
     * pode perder (chamador interno, propagacao diferente) sem aviso.
     */
    @Transactional
    public PacienteResponseDTO atualizarPaciente(UUID id, PacienteAtualizacaoDTO dto) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));

        validarCanalDeAviso(dto, paciente);

        pacienteMapper.atualizarPacienteAPartirDoDto(dto, paciente);
        aplicarLimpezaPorValorEmBranco(dto, paciente);

        boolean ligou = dto.canalAviso() == CanalAviso.EMAIL && paciente.getConsentimentoAvisoEm() == null;
        boolean desligou = paciente.getCanalAviso() != CanalAviso.EMAIL && paciente.getConsentimentoAvisoEm() != null;
        if (ligou) {
            paciente.setConsentimentoAvisoEm(LocalDateTime.now());
        }
        if (desligou) {
            paciente.setConsentimentoAvisoEm(null);
        }

        Paciente pacienteAtualizado = pacienteRepository.save(paciente);
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.ATUALIZACAO, "Paciente", pacienteAtualizado.getId());
        if (ligou || desligou) {
            auditoriaService.registrar(usuarioLogado, AcaoAuditoria.ATUALIZACAO, "ConsentimentoDoPaciente",
                    pacienteAtualizado.getId());
        }
        return pacienteMapper.toResponseDTO(pacienteAtualizado);
    }

    /**
     * Spec 5.4 no caminho da atualizacao: o canal EMAIL exige e-mail, avaliado
     * sobre o estado que o paciente TERA depois deste PUT.
     *
     * Ate a revisao da Fatia 2 isto era um validador de bean sobre o DTO, que
     * so enxergava o corpo: {@code {"canalAviso":"EMAIL"}} sozinho levava 400
     * mesmo com o e-mail ja salvo. O estado efetivo cobre os tres casos de uma
     * vez — trocar so o canal, limpar so o e-mail, e mudar os dois juntos.
     */
    private void validarCanalDeAviso(PacienteAtualizacaoDTO dto, Paciente paciente) {
        CanalAviso canalEfetivo = dto.canalAviso() != null ? dto.canalAviso() : paciente.getCanalAviso();

        String emailEfetivo = dto.email() == null
                ? paciente.getEmailPaciente()
                : (dto.email().isBlank() ? null : dto.email());

        if (canalEfetivo == CanalAviso.EMAIL && (emailEfetivo == null || emailEfetivo.isBlank())) {
            throw new EmailExigidoPeloCanalException("Informe o e-mail do paciente para avisar por e-mail.");
        }
    }

    /**
     * Campo de contato enviado em branco vira nulo na entidade.
     *
     * Gravar {@code ""} em vez de {@code null} nao serve: a coluna passaria a
     * ter dois jeitos de dizer "sem e-mail", e toda checagem de "tem contato?"
     * — inclusive a de spec 5.4 acima — teria de lembrar dos dois.
     *
     * Vale so para e-mail e telefone. {@code valorSessao}, {@code canalAviso} e
     * {@code dataNascimento} ficam de fora por decisao do dono: nenhuma tela
     * pede a limpeza deles, e {@code ""} nem valor valido para eles seria.
     */
    private void aplicarLimpezaPorValorEmBranco(PacienteAtualizacaoDTO dto, Paciente paciente) {
        if (dto.email() != null && dto.email().isBlank()) {
            paciente.setEmailPaciente(null);
        }
        if (dto.telefone() != null && dto.telefone().isBlank()) {
            paciente.setTelefonePaciente(null);
        }
    }

    /**
     * Exclusao logica do paciente. Cancela junto as sessoes ainda nao
     * realizadas (spec 5.10, decisao do dono 2026-09-05): sem isso elas
     * continuam aparecendo em Hoje/Agenda com o nome do paciente ja excluido.
     *
     * O historico fica intacto de proposito: consulta com data no passado, ou
     * ja REALIZADA/FALTOU, nao e tocada — prontuario tem prazo legal de
     * guarda (spec 7.3), e sessao que aconteceu e fato, nao agendamento.
     * "Cancelar" aqui e mudar o status para CANCELADA, nunca excluir a
     * consulta (isso e outro fluxo, cancelarSerie, que soft-deleta).
     */
    @Transactional
    public void deletarPaciente(UUID id) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));

        LocalDateTime agora = LocalDateTime.now();
        List<Consulta> aCancelar = consultaRepository.findByPacienteAndUsuarioAndDeletedAtIsNull(paciente, usuarioLogado)
                .stream()
                .filter(consulta -> !consulta.getDtConsulta().isBefore(agora))
                .filter(consulta -> consulta.getStatus() != Status.REALIZADA
                        && consulta.getStatus() != Status.FALTOU
                        && consulta.getStatus() != Status.CANCELADA)
                .toList();

        aCancelar.forEach(consulta -> consulta.setStatus(Status.CANCELADA));
        consultaRepository.saveAll(aCancelar);
        aCancelar.forEach(consulta -> auditoriaService.registrar(usuarioLogado, AcaoAuditoria.ATUALIZACAO, "Consulta",
                consulta.getId()));

        paciente.setDeletedAt(agora);
        pacienteRepository.save(paciente);
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.EXCLUSAO, "Paciente", paciente.getId());
    }

}
