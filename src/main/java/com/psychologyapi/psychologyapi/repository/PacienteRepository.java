package com.psychologyapi.psychologyapi.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;

public interface PacienteRepository extends JpaRepository<Paciente, UUID>{

    List<Paciente> findByUsuarioAndNomePacienteContainingIgnoreCase(Usuario usuario, String nome);
    Optional<Paciente> findByIdAndUsuario(UUID id, Usuario usuario);
    
}
