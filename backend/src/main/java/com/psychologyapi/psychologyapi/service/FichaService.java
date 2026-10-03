package com.psychologyapi.psychologyapi.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
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

    /**
     * O rotulo de entidade que a abertura da ficha grava na trilha.
     *
     * Deliberadamente DIFERENTE de "Paciente", que e o que
     * {@code PacienteService.buscarPorId} grava. Ate a revisao da Fatia 2 os
     * dois gravavam a mesma tripla {@code (VISUALIZACAO, "Paciente", id)}, e
     * numa trilha de acesso a prontuario isso apaga justamente a distincao que
     * importa: "abriu o prontuario inteiro" e "leu o cabecalho numa listagem"
     * viravam a mesma linha, sem como separar depois do fato (spec 7.4).
     *
     * Constante publica porque o teste de trilha afirma sobre ela — o rotulo e
     * contrato da auditoria, nao literal solto. O filtro {@code ?entidade} de
     * {@code GET /auditoria} e string livre vinda do cliente, entao nada no
     * backend quebra com o rotulo novo; o que existe hoje continua valendo.
     */
    public static final String ENTIDADE_AUDITADA = "FichaDoPaciente";

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
                        anotacoesPorSessao.getOrDefault(c.getId(), List.of()),
                        c.getValor(),
                        c.getPagoEm()))
                .toList();

        // Sim, isto duplica a consulta e o mapeamento que PendenciaService.
        // listarPorPaciente ja faz — e a duplicacao e DELIBERADA, nao esquecimento.
        // Delegar arrastaria junto o que aquele metodo faz de diferente: ele
        // resolve o paciente de novo (segunda ida ao banco para algo que ja
        // temos em maos) e e paginado por contrato. Abrir a ficha nao e "listar
        // pendencias": e uma leitura so, ja autorizada, de tudo de uma vez.
        // Antes de "consertar" unificando, pergunte-se o que acontece com a
        // trilha e com o numero de consultas ao banco.
        //
        // Assim como GET /pacientes/{id}/pendencias, a ficha devolve tambem as
        // pendencias CONCLUIDAS: a tela risca a concluida em vez de some-la, e
        // esconder aqui divergiria das duas rotas.
        List<PendenciaResponseDTO> pendencias = pendenciaRepository
                .findByPacienteAndPaciente_UsuarioAndDeletedAtIsNullOrderByCreatedAtDesc(
                        paciente, usuario, Pageable.unpaged())
                .stream()
                .map(pendenciaMapper::toResponseDTO)
                .toList();

        // abrir ficha e acesso a prontuario: registrar e exigencia, nao enfeite
        auditoriaService.registrar(usuario, AcaoAuditoria.VISUALIZACAO, ENTIDADE_AUDITADA, paciente.getId());

        return new FichaDoPacienteDTO(
                pacienteMapper.toResponseDTO(paciente), linhaDoTempo, pendencias);
    }
}
