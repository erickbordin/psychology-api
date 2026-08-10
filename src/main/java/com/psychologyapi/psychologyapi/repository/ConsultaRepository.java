package com.psychologyapi.psychologyapi.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.psychologyapi.psychologyapi.model.Consulta;
import com.psychologyapi.psychologyapi.model.Usuario;

public interface ConsultaRepository extends JpaRepository<Consulta, UUID>{
    
    List<Consulta> findByUsuarioAndDtConsultaBetween(Usuario usuario, LocalDateTime inicio, LocalDateTime fim);

}
