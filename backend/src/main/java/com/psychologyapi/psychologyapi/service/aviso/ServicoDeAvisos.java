package com.psychologyapi.psychologyapi.service.aviso;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.psychologyapi.psychologyapi.model.EstadoAviso;
import com.psychologyapi.psychologyapi.repository.AvisoDeConsultaRepository;
import com.psychologyapi.psychologyapi.repository.ResumoDiarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ServicoDeAvisos {

    private static final Logger LOG = LoggerFactory.getLogger(ServicoDeAvisos.class);

    private final Clock clock;
    private final RegistradorDeAvisos registradora;
    private final ProcessadorDeAvisos processador;
    private final AvisoDeConsultaRepository avisoRepository;
    private final ResumoDiarioRepository resumoRepository;

    public void passada() {
        passada(LocalDateTime.now(clock));
    }

    public void passada(LocalDateTime agora) {
        registradora.registrarAvisosNovos(agora);
        registradora.registrarResumos(agora);
        avisoRepository.idsPendentesAte(EstadoAviso.PENDENTE, agora)
                .forEach(id -> isolado("aviso", id, () -> processador.processarAviso(id, agora)));
        resumoRepository.idsPendentesAte(EstadoAviso.PENDENTE, agora)
                .forEach(id -> isolado("resumo", id, () -> processador.processarResumo(id, agora)));
    }

    private void isolado(String tipo, UUID id, Runnable acao) {
        try {
            acao.run();
        } catch (RuntimeException e) {
            LOG.warn("Falha ao processar {} {}: {}", tipo, id, e.getClass().getSimpleName());
        }
    }
}
