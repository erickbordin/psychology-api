package com.psychologyapi.psychologyapi.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.controller.dto.consulta.ConflitoResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaAtualizacaoDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaRecorrenteCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.ConsultaResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.SerieCancelamentoResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.SerieConsultaResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.consulta.StatusConsultaDTO;
import com.psychologyapi.psychologyapi.exceptions.ConflitoDeHorarioException;
import com.psychologyapi.psychologyapi.exceptions.ConsultaNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.exceptions.PeriodoInvalidoException;
import com.psychologyapi.psychologyapi.exceptions.SessaoComAnotacaoException;
import com.psychologyapi.psychologyapi.exceptions.SessaoComBaixaException;
import com.psychologyapi.psychologyapi.exceptions.TransicaoDeStatusInvalidaException;
import com.psychologyapi.psychologyapi.mapper.ConsultaMapper;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Status;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.AnotacaoRepository;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.service.dinheiro.ValorDaSessao;
import com.psychologyapi.psychologyapi.service.security.SecurityService;
import com.psychologyapi.psychologyapi.service.serie.OcorrenciasSemanais;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConsultaService {

    private static final long DURACAO_CONSULTA_MINUTOS = 50;

    private final ConsultaMapper consultaMapper;
    private final ConsultaRepository consultaRepository;
    private final PacienteRepository pacienteRepository;
    /** So para a guarda de deletarConsulta: sessao com anotacao viva nao e
     *  excluida (decisao do dono, revisao da Fatia 2). */
    private final AnotacaoRepository anotacaoRepository;
    private final SecurityService securityService;
    private final AuditoriaService auditoriaService;

    @Transactional
    public ConsultaResponseDTO cadastrar(ConsultaCadastroDTO dto) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();

        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(dto.pacienteId(), usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));

        Consulta consulta = consultaMapper.toEntity(dto);
        consulta.setUsuario(usuarioLogado);
        consulta.setPaciente(paciente);

        if (dto.dtConsulta() != null) {
            LocalDateTime inicioJanela = dto.dtConsulta().minusMinutes(DURACAO_CONSULTA_MINUTOS);
            LocalDateTime fimJanela = dto.dtConsulta().plusMinutes(DURACAO_CONSULTA_MINUTOS);

            List<Consulta> possiveisConflitos = consultaRepository
                    .findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(usuarioLogado, inicioJanela, fimJanela);

            boolean existeConflito = possiveisConflitos.stream()
                    .anyMatch(c -> c.getStatus() != Status.CANCELADA);

            if (existeConflito) {
                throw new ConflitoDeHorarioException("Ja tem agendamento para este horario");
            }
        }

        Consulta consultaSalva = consultaRepository.save(consulta);
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.CRIACAO, "Consulta", consultaSalva.getId());
        return consultaMapper.toResponseDTO(consultaSalva);
    }

    /** Responde se o horario esta livre na agenda DESTE psicologo, sem criar
     *  nada (spec 5.6). Conflito e por psicologo: dois profissionais podem
     *  ter paciente as 14h da mesma terca sem que um bloqueie o outro.
     *
     *  Usa a MESMA janela de DURACAO_CONSULTA_MINUTOS que os caminhos de
     *  gravacao. Comparar inicio exato aqui faria este endpoint responder
     *  "livre" para um horario que o POST recusaria — o front perguntaria,
     *  receberia sim, enviaria e apanharia, que e a falha que esta rota
     *  existe para eliminar.
     *
     *  excluirConsultaId (opcional, nulo no fluxo de criacao) tira a propria
     *  consulta sendo editada da checagem — mesmo filtro que
     *  atualizarConsulta ja aplica na escrita. Sem isso, revalidar o horario
     *  de uma consulta existente a encontraria como seu proprio conflito. */
    @Transactional(readOnly = true)
    public ConflitoResponseDTO checarConflito(LocalDateTime dtConsulta, UUID excluirConsultaId) {
        Usuario usuario = securityService.obterUsuarioLogado();

        LocalDateTime inicioJanela = dtConsulta.minusMinutes(DURACAO_CONSULTA_MINUTOS);
        LocalDateTime fimJanela = dtConsulta.plusMinutes(DURACAO_CONSULTA_MINUTOS);

        return consultaRepository
                .findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(usuario, inicioJanela, fimJanela)
                .stream()
                .filter(c -> c.getStatus() != Status.CANCELADA)
                .filter(c -> excluirConsultaId == null || !c.getId().equals(excluirConsultaId))
                .filter(c -> dentroDaJanela(c.getDtConsulta(), dtConsulta))
                .findFirst()
                .map(c -> new ConflitoResponseDTO(
                        false, c.getPaciente().getNomePaciente(), c.getId()))
                .orElseGet(ConflitoResponseDTO::horarioLivre);
    }

    public ConsultaResponseDTO buscarPorId(UUID id) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Consulta consulta = consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, usuarioLogado)
                .orElseThrow(() -> new ConsultaNaoEncontradaException("Consulta nao encontrada"));
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.VISUALIZACAO, "Consulta", consulta.getId());
        return consultaMapper.toResponseDTO(consulta);
    }

    public List<ConsultaResponseDTO> listarAgendaDoDia() {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();

        LocalDateTime inicioDoDia = LocalDate.now().atStartOfDay();
        LocalDateTime fimDoDia = LocalDate.now().atTime(LocalTime.MAX);

        return buscarEMapearAgenda(usuarioLogado, inicioDoDia, fimDoDia);
    }

    /**
     * 'de' e 'ate' sao opcionais, mas nao independentes: com os dois ausentes
     * preserva o contrato antigo (agenda de hoje, via listarAgendaDoDia); com
     * so um deles ou com o intervalo invertido e erro do cliente (400), nunca
     * uma adivinhacao silenciosa de intervalo.
     */
    public List<ConsultaResponseDTO> listarAgenda(LocalDate de, LocalDate ate) {
        if (de == null && ate == null) {
            return listarAgendaDoDia();
        }

        if (de == null || ate == null) {
            String campoAusente = de == null ? "de" : "ate";
            throw new PeriodoInvalidoException(campoAusente,
                    "Informe 'de' e 'ate' juntos, ou nenhum dos dois.");
        }

        if (ate.isBefore(de)) {
            throw new PeriodoInvalidoException("ate", "'ate' nao pode ser anterior a 'de'.");
        }

        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        return buscarEMapearAgenda(usuarioLogado, de.atStartOfDay(), ate.atTime(LocalTime.MAX));
    }

    private List<ConsultaResponseDTO> buscarEMapearAgenda(Usuario usuario, LocalDateTime inicio, LocalDateTime fim) {
        List<Consulta> consultas = consultaRepository
                .findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(usuario, inicio, fim);

        return consultas.stream()
                .map(consultaMapper::toResponseDTO)
                .toList();
    }

    @Transactional
    public ConsultaResponseDTO atualizarConsulta(UUID id, ConsultaAtualizacaoDTO dto) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();

        Consulta consulta = consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, usuarioLogado)
                .orElseThrow(() -> new ConsultaNaoEncontradaException("Consulta nao encontrada"));

        if (consulta.getPagoEm() != null && dto.status() != null && dto.status() != Status.REALIZADA) {
            throw new SessaoComBaixaException(SessaoComBaixaException.AO_MUDAR_STATUS);
        }

        Status statusAnterior = consulta.getStatus();
        consultaMapper.atualizarConsultaApartirDoDTO(dto, consulta);
        if (statusAnterior != Status.REALIZADA) {
            ValorDaSessao.congelarSeRealizada(consulta);
        }

        if (dto.dtConsulta() != null) {
            LocalDateTime inicioJanela = dto.dtConsulta().minusMinutes(DURACAO_CONSULTA_MINUTOS);
            LocalDateTime fimJanela = dto.dtConsulta().plusMinutes(DURACAO_CONSULTA_MINUTOS);

            List<Consulta> possiveisConflitos = consultaRepository
                    .findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(usuarioLogado, inicioJanela, fimJanela);

            boolean existeConflito = possiveisConflitos.stream()
                    .anyMatch(c -> c.getStatus() != Status.CANCELADA
                            && !c.getId().equals(consulta.getId()));

            if (existeConflito) {
                throw new ConflitoDeHorarioException("Ja tem agendamento para este horario");
            }
        }

        Consulta consultaAtualizada = consultaRepository.save(consulta);
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.ATUALIZACAO, "Consulta", consultaAtualizada.getId());
        return consultaMapper.toResponseDTO(consultaAtualizada);
    }

    /**
     * PATCH /consultas/{id}/status (spec 4①) — so troca o status, nunca a
     * data. Por isso NAO roda a checagem de conflito de atualizarConsulta:
     * dtConsulta fica intocada, entao nao ha novo horario para conflitar, e
     * rodar a checagem aqui poderia recusar marcar uma sessao passada como
     * REALIZADA por causa de uma vizinha na mesma janela de 50 minutos.
     */
    @Transactional
    public ConsultaResponseDTO atualizarStatus(UUID id, StatusConsultaDTO dto) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();

        Consulta consulta = consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, usuarioLogado)
                .orElseThrow(() -> new ConsultaNaoEncontradaException("Consulta nao encontrada"));

        // O @StatusDeFechamento no DTO so restringe o DESTINO (REALIZADA/
        // FALTOU); ele e uma constraint de campo e estruturalmente nao ve o
        // status ATUAL da entidade. A brecha de duplo agendamento nao esta no
        // destino — os dois destinos sao != CANCELADA de qualquer forma —,
        // esta em SAIR de CANCELADA sem checagem nenhuma: cancelar A libera o
        // horario para B (todo caminho de escrita trata CANCELADA como vago),
        // e reabrir A por aqui devolveria A e B ativas ao mesmo tempo.
        //
        // Por isso a guarda e aqui, sobre o estado atual, e nao mais uma copia
        // da checagem de conflito. Recusar em vez de checar conflito de
        // proposito: reabrir uma sessao cancelada e um REAGENDAMENTO, e
        // PUT /consultas/{id} ja faz reagendamento com seguranca (roda a
        // checagem de janela; responde 409 se o horario foi dado a outra
        // pessoa). Marcar presenca e para uma sessao que aconteceu. Rodar a
        // checagem de conflito aqui tambem criaria uma QUARTA copia da mesma
        // regra de janela no codigo, e o laudo do conjunto ja aponta que duas
        // copias sem nada as amarrando ja e defeito por si so.
        if (consulta.getStatus() == Status.CANCELADA) {
            throw new TransicaoDeStatusInvalidaException(
                    "Esta sessão foi cancelada. Reagende-a para poder marcar presença.");
        }

        if (consulta.getPagoEm() != null && dto.status() != Status.REALIZADA) {
            throw new SessaoComBaixaException(SessaoComBaixaException.AO_MUDAR_STATUS);
        }

        Status statusAnterior = consulta.getStatus();
        consulta.setStatus(dto.status());
        if (statusAnterior != Status.REALIZADA) {
            ValorDaSessao.congelarSeRealizada(consulta);
        }

        Consulta salva = consultaRepository.save(consulta);
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.ATUALIZACAO, "Consulta", salva.getId());
        return consultaMapper.toResponseDTO(salva);
    }

    /**
     * DELETE /consultas/{id} — exclusao logica da sessao.
     *
     * RECUSA com 409 quando a sessao tem anotacao viva presa a ela (decisao do
     * dono, revisao da Fatia 2). O soft delete da consulta nunca apagou a
     * anotacao: ela sobrevivia apontando para uma consulta excluida e sumia da
     * ficha, porque {@code FichaService} so lista consulta nao apagada. O
     * resultado era conteudo clinico invisivel no prontuario, sem aviso nenhum
     * — e prontuario tem prazo legal de guarda (spec 7.3).
     *
     * A guarda vem ANTES do {@code setDeletedAt}: nada e mutado num caminho que
     * vai ser recusado. Sessao sem anotacao segue exatamente como antes, com
     * 204.
     *
     * Nao vale para {@code cancelarSerie}, que tambem soft-deleta: aquele fluxo
     * so alcanca ocorrencias FUTURAS, e sessao que ainda nao aconteceu nao tem
     * anotacao de sessao para perder.
     */
    @Transactional
    public void deletarConsulta(UUID id) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Consulta consulta = consultaRepository.findByIdAndUsuarioAndDeletedAtIsNull(id, usuarioLogado)
                .orElseThrow(() -> new ConsultaNaoEncontradaException("Consulta nao encontrada"));

        if (anotacaoRepository.existsByConsultaAndDeletedAtIsNull(consulta)) {
            throw new SessaoComAnotacaoException(
                    "Esta sessão tem anotação registrada. Exclua a anotação antes de excluir a sessão.");
        }

        if (consulta.getPagoEm() != null) {
            throw new SessaoComBaixaException(SessaoComBaixaException.AO_EXCLUIR);
        }

        consulta.setDeletedAt(LocalDateTime.now());
        consultaRepository.save(consulta);
        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.EXCLUSAO, "Consulta", consulta.getId());
    }

    @Transactional
    public SerieConsultaResponseDTO cadastrarSerie(ConsultaRecorrenteCadastroDTO dto) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();

        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(dto.pacienteId(), usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));

        List<LocalDateTime> ocorrencias = dto.quantidadeSessoes() != null
                ? OcorrenciasSemanais.aPartirDe(dto.dtPrimeiraConsulta(), dto.quantidadeSessoes())
                : OcorrenciasSemanais.ate(dto.dtPrimeiraConsulta(), dto.dtLimite(),
                        OcorrenciasSemanais.MAX_OCORRENCIAS);

        validarTamanhoDaSerie(ocorrencias);
        validarConflitoDaSerie(usuarioLogado, ocorrencias);

        UUID serieId = UUID.randomUUID();
        List<Consulta> novas = ocorrencias.stream()
                .map(dtConsulta -> {
                    Consulta consulta = new Consulta();
                    consulta.setUsuario(usuarioLogado);
                    consulta.setPaciente(paciente);
                    consulta.setDtConsulta(dtConsulta);
                    consulta.setSerieId(serieId);
                    return consulta;
                })
                .toList();

        List<Consulta> salvas = consultaRepository.saveAll(novas);
        salvas.forEach(consulta -> auditoriaService.registrar(usuarioLogado, AcaoAuditoria.CRIACAO, "Consulta",
                consulta.getId()));

        return new SerieConsultaResponseDTO(serieId, salvas.size(),
                salvas.stream().map(consultaMapper::toResponseDTO).toList());
    }

    /**
     * Guarda de erro de programacao, nao caminho de validacao do usuario: quem
     * chega pela API ja passou pela constraint do DTO, que devolve 400. Existe
     * para um chamador interno (outro service, um job futuro) nao entrar aqui
     * com horizonte degenerado e estourar no get(0) da validacao de conflito.
     */
    private void validarTamanhoDaSerie(List<LocalDateTime> ocorrencias) {
        if (ocorrencias.size() < OcorrenciasSemanais.MIN_OCORRENCIAS
                || ocorrencias.size() > OcorrenciasSemanais.MAX_OCORRENCIAS) {
            throw new IllegalArgumentException("A serie deve ter entre " + OcorrenciasSemanais.MIN_OCORRENCIAS
                    + " e " + OcorrenciasSemanais.MAX_OCORRENCIAS + " ocorrencias, e tem " + ocorrencias.size());
        }
    }

    /**
     * Uma query no intervalo inteiro da serie, e a janela de 50 min avaliada em
     * memoria: uma query por ocorrencia seriam ate 52 round trips.
     */
    private void validarConflitoDaSerie(Usuario usuario, List<LocalDateTime> ocorrencias) {
        LocalDateTime inicioJanela = ocorrencias.get(0).minusMinutes(DURACAO_CONSULTA_MINUTOS);
        LocalDateTime fimJanela = ocorrencias.get(ocorrencias.size() - 1).plusMinutes(DURACAO_CONSULTA_MINUTOS);

        List<Consulta> existentes = consultaRepository
                .findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(usuario, inicioJanela, fimJanela)
                .stream()
                .filter(consulta -> consulta.getStatus() != Status.CANCELADA)
                .toList();

        String conflitantes = ocorrencias.stream()
                .filter(ocorrencia -> existentes.stream()
                        .anyMatch(existente -> dentroDaJanela(existente.getDtConsulta(), ocorrencia)))
                .map(LocalDateTime::toString)
                .collect(Collectors.joining(", "));

        if (!conflitantes.isEmpty()) {
            throw new ConflitoDeHorarioException("Conflito de horario em: " + conflitantes);
        }
    }

    private boolean dentroDaJanela(LocalDateTime existente, LocalDateTime nova) {
        return !existente.isBefore(nova.minusMinutes(DURACAO_CONSULTA_MINUTOS))
                && !existente.isAfter(nova.plusMinutes(DURACAO_CONSULTA_MINUTOS));
    }

    @Transactional
    public SerieCancelamentoResponseDTO cancelarSerie(UUID serieId) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();

        List<Consulta> daSerie = consultaRepository.findBySerieIdAndUsuarioAndDeletedAtIsNull(serieId, usuarioLogado);
        if (daSerie.isEmpty()) {
            throw new ConsultaNaoEncontradaException("Serie nao encontrada");
        }

        LocalDateTime agora = LocalDateTime.now();
        List<Consulta> futuras = daSerie.stream()
                .filter(consulta -> !consulta.getDtConsulta().isBefore(agora))
                .toList();

        futuras.forEach(consulta -> consulta.setDeletedAt(agora));
        consultaRepository.saveAll(futuras);
        futuras.forEach(consulta -> auditoriaService.registrar(usuarioLogado, AcaoAuditoria.EXCLUSAO, "Consulta",
                consulta.getId()));

        return new SerieCancelamentoResponseDTO(serieId, futuras.size());
    }

    /**
     * Historico de atendimento do paciente. Paginado porque cresce sem limite com
     * o tempo, e auditado como VISUALIZACAO do paciente — e acesso ao historico de
     * alguem identificado, nao uma listagem generica.
     */
    public Page<ConsultaResponseDTO> listarPorPaciente(UUID pacienteId, Pageable pageable) {
        Usuario usuarioLogado = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, usuarioLogado)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));

        Page<Consulta> consultas = consultaRepository
                .findByPacienteAndUsuarioAndDeletedAtIsNullOrderByDtConsultaDesc(paciente, usuarioLogado, pageable);

        auditoriaService.registrar(usuarioLogado, AcaoAuditoria.VISUALIZACAO, "Paciente", paciente.getId());

        return consultas.map(consultaMapper::toResponseDTO);
    }

}
