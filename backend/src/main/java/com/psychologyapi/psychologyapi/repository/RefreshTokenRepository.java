package com.psychologyapi.psychologyapi.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.psychologyapi.psychologyapi.model.RefreshToken;
import com.psychologyapi.psychologyapi.model.Usuario;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByHashToken(String hashToken);

    List<RefreshToken> findByUsuarioAndRevokedAtIsNull(Usuario usuario);

    void deleteByUsuarioAndExpiresAtBefore(Usuario usuario, LocalDateTime momento);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken r WHERE r.usuario = :usuario")
    int apagarTodosDe(@Param("usuario") Usuario usuario);

}
