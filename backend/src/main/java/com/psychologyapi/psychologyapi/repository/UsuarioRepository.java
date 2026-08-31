package com.psychologyapi.psychologyapi.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.psychologyapi.psychologyapi.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID>{

    Optional<Usuario> findByEmailUsuario(String email);
    boolean existsByEmailUsuario(String email);
}
