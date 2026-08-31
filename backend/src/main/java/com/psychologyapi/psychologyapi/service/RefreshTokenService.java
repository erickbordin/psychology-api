package com.psychologyapi.psychologyapi.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.exceptions.SessaoInvalidaException;
import com.psychologyapi.psychologyapi.model.RefreshToken;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int BYTES_DO_TOKEN = 32;
    private static final String MENSAGEM_SESSAO_INVALIDA = "Sessão inválida. Entre novamente.";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.refresh.expiracao-dias}")
    private int expiracaoDias;

    public RefreshEmitido emitir(Usuario usuario) {
        String tokenEmClaro = gerarTokenEmClaro();

        RefreshToken registro = new RefreshToken();
        registro.setUsuario(usuario);
        registro.setHashToken(hashDe(tokenEmClaro));
        registro.setExpiresAt(LocalDateTime.now().plusDays(expiracaoDias));

        return new RefreshEmitido(tokenEmClaro, refreshTokenRepository.save(registro));
    }

    private String gerarTokenEmClaro() {
        byte[] bytes = new byte[BYTES_DO_TOKEN];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256, e nao BCrypt como na senha: a validacao precisa ENCONTRAR a linha a
     * partir do token apresentado, e hash com salt por linha obrigaria a varrer a
     * tabela inteira. O que justifica BCrypt na senha — entropia baixa, ataque de
     * dicionario — nao se aplica a um valor aleatorio de 256 bits.
     */
    static String hashDe(String tokenEmClaro) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(tokenEmClaro.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", e);
        }
    }

    /**
     * Rotaciona a cada uso. Um token ja revogado apresentado de novo significa que
     * duas partes tem a mesma credencial — a legitima e uma copia. Revogar a cadeia
     * inteira derruba as duas, e so o dono consegue voltar, porque so ele tem a senha.
     */
    @Transactional
    public RefreshEmitido rotacionar(String tokenEmClaro) {
        RefreshToken atual = refreshTokenRepository.findByHashToken(hashDe(tokenEmClaro))
                .orElseThrow(() -> new SessaoInvalidaException(MENSAGEM_SESSAO_INVALIDA));

        if (atual.getRevokedAt() != null) {
            revogarCadeiaDe(atual.getUsuario());
            throw new SessaoInvalidaException(MENSAGEM_SESSAO_INVALIDA);
        }

        if (atual.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new SessaoInvalidaException(MENSAGEM_SESSAO_INVALIDA);
        }

        RefreshEmitido proximo = emitir(atual.getUsuario());
        atual.setRevokedAt(LocalDateTime.now());
        atual.setSubstituidoPor(proximo.registro().getId());
        refreshTokenRepository.save(atual);

        return proximo;
    }

    @Transactional
    public void revogar(String tokenEmClaro) {
        if (tokenEmClaro == null || tokenEmClaro.isBlank()) {
            return;
        }
        refreshTokenRepository.findByHashToken(hashDe(tokenEmClaro))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> {
                    token.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void limparExpiradosDe(Usuario usuario) {
        refreshTokenRepository.deleteByUsuarioAndExpiresAtBefore(usuario, LocalDateTime.now());
    }

    private void revogarCadeiaDe(Usuario usuario) {
        LocalDateTime agora = LocalDateTime.now();
        List<RefreshToken> vivos = refreshTokenRepository.findByUsuarioAndRevokedAtIsNull(usuario);
        vivos.forEach(token -> token.setRevokedAt(agora));
        refreshTokenRepository.saveAll(vivos);
    }

}
