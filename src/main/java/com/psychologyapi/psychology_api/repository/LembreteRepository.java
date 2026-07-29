package com.psychologyapi.psychology_api.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.psychologyapi.psychology_api.model.Lembrete;

public interface LembreteRepository extends JpaRepository<Lembrete, UUID>{
    
}
