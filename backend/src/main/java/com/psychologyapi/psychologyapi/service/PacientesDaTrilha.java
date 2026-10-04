package com.psychologyapi.psychologyapi.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.psychologyapi.psychologyapi.model.Anotacao;
import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Pendencia;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.LogAuditoria;
import com.psychologyapi.psychologyapi.repository.AnotacaoRepository;
import com.psychologyapi.psychologyapi.repository.ConsultaRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.repository.PendenciaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PacientesDaTrilha {

    private static final Set<String> DO_PROPRIO_PACIENTE =
            Set.of("Paciente", "FichaDoPaciente", "ConsentimentoDoPaciente", "DescadastroDoPaciente");

    private final PacienteRepository pacienteRepository;
    private final ConsultaRepository consultaRepository;
    private final AnotacaoRepository anotacaoRepository;
    private final PendenciaRepository pendenciaRepository;

    public Map<UUID, Paciente> porLinha(Usuario usuario, List<LogAuditoria> linhas) {
        Map<String, Set<UUID>> idsPorEntidade = new HashMap<>();
        linhas.stream()
                .filter(linha -> linha.getEntidadeId() != null)
                .forEach(linha -> idsPorEntidade
                        .computeIfAbsent(linha.getEntidade(), e -> new HashSet<>())
                        .add(linha.getEntidadeId()));

        Map<String, Map<UUID, Paciente>> resolvidos = new HashMap<>();

        Set<UUID> diretos = new HashSet<>();
        DO_PROPRIO_PACIENTE.forEach(e -> diretos.addAll(idsPorEntidade.getOrDefault(e, Set.of())));
        Map<UUID, Paciente> pacientes = diretos.isEmpty() ? Map.of()
                : pacienteRepository.findByIdInAndUsuario(diretos, usuario).stream()
                        .collect(Collectors.toMap(Paciente::getId, p -> p));
        DO_PROPRIO_PACIENTE.forEach(e -> resolvidos.put(e, pacientes));

        resolvidos.put("Consulta", pacientesDe(idsPorEntidade.get("Consulta"),
                ids -> consultaRepository.findByIdInAndUsuario(ids, usuario), Consulta::getId, Consulta::getPaciente));
        resolvidos.put("Anotacao", pacientesDe(idsPorEntidade.get("Anotacao"),
                ids -> anotacaoRepository.findByIdInAndUsuario(ids, usuario), Anotacao::getId, Anotacao::getPaciente));
        resolvidos.put("Pendencia", pacientesDe(idsPorEntidade.get("Pendencia"),
                ids -> pendenciaRepository.findByIdInAndPaciente_Usuario(ids, usuario), Pendencia::getId,
                Pendencia::getPaciente));

        Map<UUID, Paciente> porLinha = new HashMap<>();
        linhas.forEach(linha -> {
            if (linha.getEntidadeId() == null) {
                return;
            }
            Paciente paciente = resolvidos.getOrDefault(linha.getEntidade(), Map.of()).get(linha.getEntidadeId());
            if (paciente != null) {
                porLinha.put(linha.getId(), paciente);
            }
        });
        return porLinha;
    }

    private <E> Map<UUID, Paciente> pacientesDe(Set<UUID> ids, Function<Set<UUID>, List<E>> buscar,
            Function<E, UUID> id, Function<E, Paciente> paciente) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return buscar.apply(ids).stream().collect(Collectors.toMap(id, paciente));
    }
}
