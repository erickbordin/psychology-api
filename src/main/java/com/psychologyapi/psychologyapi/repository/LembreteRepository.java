package com.psychologyapi.psychologyapi.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.psychologyapi.psychologyapi.model.Lembrete;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;

public interface LembreteRepository extends JpaRepository<Lembrete, UUID>{

    Optional<Lembrete> findByIdAndPaciente_UsuarioAndDeletedAtIsNull(UUID id, Usuario usuario);
    Page<Lembrete> findByPacienteAndPaciente_UsuarioAndDeletedAtIsNullOrderByCreatedAtDesc(Paciente paciente, Usuario usuario, Pageable pageable);
    
}
