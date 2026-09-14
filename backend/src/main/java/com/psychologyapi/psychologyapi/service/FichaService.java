package com.psychologyapi.psychologyapi.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.controller.dto.ficha.AnotacaoDaSessaoDTO;
import com.psychologyapi.psychologyapi.controller.dto.ficha.FichaDoPacienteDTO;
import com.psychologyapi.psychologyapi.controller.dto.ficha.SessaoDaFichaDTO;
import com.psychologyapi.psychologyapi.controller.dto.pendencia.PendenciaResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.mapper.PacienteMapper;
import com.psychologyapi.psychologyapi.mapper.PendenciaMapper;
import com.psychologyapi.psychologyapi.model.Anotacao;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.AnotacaoRepository;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.PendenciaRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FichaService {

    private final SecurityService securityService;
    private final PacienteRepository pacienteRepository;
    private final ConsultaRepository consultaRepository;
    private final AnotacaoRepository anotacaoRepository;
    private final PendenciaRepository pendenciaRepository;
    private final PacienteMapper pacienteMapper;
    private final PendenciaMapper pendenciaMapper;
    private final AuditoriaService auditoriaService;

    /**
     * Transacional pelo mesmo motivo de PainelService.hoje(): o DTO e montado
     * a partir de associacoes LAZY, e sem transacao o acesso vira
     * LazyInitializationException.
     *
     * Mas AO CONTRARIO de PainelService.hoje(), esta NAO pode ser readOnly:
     * abrir a ficha grava o log de auditoria, e readOnly poe a sessao do
     * Hibernate em FlushMode.MANUAL — o insert do log seria descartado no
     * commit, sem erro nenhum. Auditoria de acesso a prontuario sumindo em
     * silencio e o pior modo de falhar desta rota (spec 7.4).
     *
     * Tres consultas ao banco, nao N+1: uma de sessoes, uma de anotacoes com
     * sessao, uma de pendencias. A juncao acontece em memoria sobre um mapa
     * por consultaId — volume de autonomo e pequeno, mas uma query por linha
     * da ficha seria erro de graca.
     */
    @Transactional
    public FichaDoPacienteDTO doPaciente(UUID pacienteId) {
        Usuario usuario = securityService.obterUsuarioLogado();
        Paciente paciente = pacienteRepository.findByIdAndUsuarioAndDeletedAtIsNull(pacienteId, usuario)
                .orElseThrow(() -> new PacienteNaoEncontradoException("Paciente nao encontrado"));

        Map<UUID, List<AnotacaoDaSessaoDTO>> anotacoesPorSessao = anotacaoRepository
                .findByPacienteAndUsuarioAndConsultaIsNotNullAndDeletedAtIsNullOrderByCreatedAtAsc(paciente, usuario)
                .stream()
                .collect(Collectors.groupingBy(
                        (Anotacao a) -> a.getConsulta().getId(),
                        Collectors.mapping(
                                a -> new AnotacaoDaSessaoDTO(a.getId(), a.getConteudo(), a.getCreatedAt()),
                                Collectors.toList())));

        List<SessaoDaFichaDTO> linhaDoTempo = consultaRepository
                .findByPacienteAndUsuarioAndDeletedAtIsNull(paciente, usuario)
                .stream()
                // do encontro mais recente para o mais antigo: a ficha e aberta
                // para saber o que aconteceu na ultima sessao
                .sorted(Comparator.comparing(c -> c.getDtConsulta(), Comparator.reverseOrder()))
                .map(c -> new SessaoDaFichaDTO(
                        c.getId(),
                        c.getDtConsulta(),
                        c.getStatus(),
                        anotacoesPorSessao.getOrDefault(c.getId(), List.of())))
                .toList();

        List<PendenciaResponseDTO> pendencias = pendenciaRepository
                .findByPacienteAndPaciente_UsuarioAndDeletedAtIsNullOrderByCreatedAtDesc(
                        paciente, usuario, org.springframework.data.domain.Pageable.unpaged())
                .stream()
                .map(pendenciaMapper::toResponseDTO)
                .toList();

        // abrir ficha e acesso a prontuario: registrar e exigencia, nao enfeite
        auditoriaService.registrar(usuario, AcaoAuditoria.VISUALIZACAO, "Paciente", paciente.getId());

        return new FichaDoPacienteDTO(
                pacienteMapper.toResponseDTO(paciente), linhaDoTempo, pendencias);
    }
}
