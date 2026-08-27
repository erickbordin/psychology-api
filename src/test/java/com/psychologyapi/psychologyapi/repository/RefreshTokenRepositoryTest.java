package com.psychologyapi.psychologyapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.psychologyapi.psychologyapi.model.RefreshToken;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.PostgresContainerConfig;

/**
 * O lookup por hash e o caminho quente da renovacao, e a limpeza de expirados
 * apaga linha de verdade — as duas coisas so se provam contra um banco real.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class RefreshTokenRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("encontra o token pelo hash")
    void encontraPeloHash() {
        Usuario dono = em.persistFlushFind(Fixtures.usuario("dono@teste.com"));
        em.persistFlushFind(Fixtures.refreshToken(dono, "a".repeat(64), LocalDateTime.now().plusDays(14)));

        Optional<RefreshToken> encontrado = refreshTokenRepository.findByHashToken("a".repeat(64));

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getUsuario().getId()).isEqualTo(dono.getId());
    }

    @Test
    @DisplayName("lista apenas os tokens vivos do usuario")
    void listaApenasOsVivos() {
        Usuario dono = em.persistFlushFind(Fixtures.usuario("dono@teste.com"));
        em.persistFlushFind(Fixtures.refreshToken(dono, "b".repeat(64), LocalDateTime.now().plusDays(14)));

        RefreshToken revogado = Fixtures.refreshToken(dono, "c".repeat(64), LocalDateTime.now().plusDays(14));
        revogado.setRevokedAt(LocalDateTime.now());
        em.persistFlushFind(revogado);

        assertThat(refreshTokenRepository.findByUsuarioAndRevokedAtIsNull(dono))
                .extracting(RefreshToken::getHashToken)
                .containsExactly("b".repeat(64));
    }

    @Test
    @DisplayName("apaga so os expirados do usuario informado")
    void apagaSoOsExpiradosDoUsuario() {
        Usuario dono = em.persistFlushFind(Fixtures.usuario("dono@teste.com"));
        Usuario outro = em.persistFlushFind(Fixtures.usuario("outro@teste.com"));

        em.persistFlushFind(Fixtures.refreshToken(dono, "d".repeat(64), LocalDateTime.now().minusDays(1)));
        em.persistFlushFind(Fixtures.refreshToken(dono, "e".repeat(64), LocalDateTime.now().plusDays(14)));
        em.persistFlushFind(Fixtures.refreshToken(outro, "f".repeat(64), LocalDateTime.now().minusDays(1)));

        refreshTokenRepository.deleteByUsuarioAndExpiresAtBefore(dono, LocalDateTime.now());
        em.flush();

        assertThat(refreshTokenRepository.findByHashToken("d".repeat(64))).isEmpty();
        assertThat(refreshTokenRepository.findByHashToken("e".repeat(64))).isPresent();
        assertThat(refreshTokenRepository.findByHashToken("f".repeat(64))).isPresent();
    }
}
