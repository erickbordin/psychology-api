package com.psychologyapi.psychologyapi.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Usuario;

public interface ConsultaRepository extends JpaRepository<Consulta, UUID>{

    List<Consulta> findByUsuarioAndDtConsultaBetweenAndDeletedAtIsNull(Usuario usuario, LocalDateTime inicio, LocalDateTime fim);
    Optional<Consulta> findByIdAndUsuarioAndDeletedAtIsNull(UUID id, Usuario usuario);
    List<Consulta> findBySerieIdAndUsuarioAndDeletedAtIsNull(UUID serieId, Usuario usuario);

}
