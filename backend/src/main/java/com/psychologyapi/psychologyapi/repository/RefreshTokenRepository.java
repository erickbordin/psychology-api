package com.psychologyapi.psychologyapi.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.psychologyapi.psychologyapi.model.RefreshToken;
import com.psychologyapi.psychologyapi.model.Usuario;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByHashToken(String hashToken);

    List<RefreshToken> findByUsuarioAndRevokedAtIsNull(Usuario usuario);

    void deleteByUsuarioAndExpiresAtBefore(Usuario usuario, LocalDateTime momento);

}
