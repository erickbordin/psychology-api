package com.psychologyapi.psychologyapi.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.psychologyapi.psychologyapi.model.ConviteUsado;

public interface ConviteUsadoRepository extends JpaRepository<ConviteUsado, UUID> {

    boolean existsByHash(String hash);

}
