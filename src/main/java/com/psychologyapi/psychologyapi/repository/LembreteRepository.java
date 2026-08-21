package com.psychologyapi.psychologyapi.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.psychologyapi.psychologyapi.model.Lembrete;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;

public interface LembreteRepository extends JpaRepository<Lembrete, UUID>{

    Optional<Lembrete> findByIdAndPaciente_UsuarioAndDeletedAtIsNull(UUID id, Usuario usuario);
    List<Lembrete> findByPacienteAndPaciente_UsuarioAndDeletedAtIsNullOrderByCreatedAtDesc(Paciente paciente, Usuario usuario);
    
}
