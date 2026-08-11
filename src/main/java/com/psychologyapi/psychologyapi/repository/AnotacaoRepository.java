package com.psychologyapi.psychologyapi.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import com.psychologyapi.psychologyapi.model.Anotacao;

public interface AnotacaoRepository extends JpaRepository<Anotacao, UUID>{

    Optional<Anotacao> findByIdAndUsuario(UUID id, Usuario usuario);
    List<Anotacao> findByPacienteAndUsuarioOrderByCreatedAtDesc(Paciente paciente, Usuario usuario);

}
