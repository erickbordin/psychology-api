package com.psychologyapi.psychologyapi.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;

public interface PacienteRepository extends JpaRepository<Paciente, UUID> {

    Optional<Paciente> findByIdAndUsuarioAndDeletedAtIsNull(UUID id, Usuario usuario);
    List<Paciente> findByUsuarioAndNomePacienteContainingIgnoreCaseAndDeletedAtIsNull(Usuario usuario, String nome);
    List<Paciente> findByUsuarioAndDeletedAtIsNull(Usuario usuario);
    List<Paciente> findByIdInAndUsuario(Collection<UUID> ids, Usuario usuario);
    Optional<Paciente> findByTokenDescadastroAndDeletedAtIsNull(String tokenDescadastro);
}
