package com.psychologyapi.psychology_api.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.psychologyapi.psychology_api.model.Paciente;

public interface PacienteRepository extends JpaRepository<Paciente, UUID>{

    Optional<Paciente> findByNomeContainingIgnoreCase(String nome);
    
}
