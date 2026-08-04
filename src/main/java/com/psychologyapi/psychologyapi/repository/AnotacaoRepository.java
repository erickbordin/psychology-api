package com.psychologyapi.psychologyapi.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.psychologyapi.psychologyapi.model.Anotacao;

public interface AnotacaoRepository extends JpaRepository<Anotacao, UUID>{
    
}
