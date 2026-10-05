package com.psychologyapi.psychologyapi.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.psychologyapi.psychologyapi.exceptions.ConviteInvalidoException;
import com.psychologyapi.psychologyapi.model.ConviteUsado;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.ConviteUsadoRepository;

@Service
public class ConviteService {

    private static final String CONSTRAINT_UNICA = "convite_usado_hash_convite_key";
    private static final String MENSAGEM_INVALIDO = "Código de convite inválido.";

    private final ConviteUsadoRepository conviteUsadoRepository;
    private final Clock clock;
    private final Set<String> hashesValidos;

    public ConviteService(ConviteUsadoRepository conviteUsadoRepository, Clock clock,
            @Value("${app.cadastro.convites:}") String lista) {
        this.conviteUsadoRepository = conviteUsadoRepository;
        this.clock = clock;
        this.hashesValidos = Arrays.stream(lista.split(","))
                .map(String::trim)
                .filter(codigo -> !codigo.isEmpty())
                .map(ConviteService::hash)
                .collect(Collectors.toUnmodifiableSet());
    }

    public void exigirDisponivel(String codigo) {
        String hash = hash(codigo);
        if (!hashesValidos.contains(hash) || conviteUsadoRepository.existsByHash(hash)) {
            throw new ConviteInvalidoException(MENSAGEM_INVALIDO);
        }
    }

    public void queimar(String codigo, Usuario usuario) {
        ConviteUsado usado = new ConviteUsado();
        usado.setHash(hash(codigo));
        usado.setUsuario(usuario);
        usado.setUsadoEm(LocalDateTime.now(clock));
        try {
            conviteUsadoRepository.saveAndFlush(usado);
        } catch (DataIntegrityViolationException e) {
            if (violaUnicidadeDoConvite(e)) {
                throw new ConviteInvalidoException(MENSAGEM_INVALIDO);
            }
            throw e;
        }
    }

    private static boolean violaUnicidadeDoConvite(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException violacao
                    && CONSTRAINT_UNICA.equals(violacao.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    private static String hash(String codigo) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256")
                    .digest(codigo.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

}
