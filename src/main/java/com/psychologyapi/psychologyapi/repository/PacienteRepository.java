package com.psychologyapi.psychologyapi.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.psychologyapi.psychologyapi.model.Paciente;

public interface PacienteRepository extends JpaRepository<Paciente, UUID>{

    Optional<Paciente> findByNomePacienteContainingIgnoreCase(String nome);
    
}
