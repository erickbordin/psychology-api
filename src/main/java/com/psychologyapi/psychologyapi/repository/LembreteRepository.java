package com.psychologyapi.psychologyapi.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.psychologyapi.psychologyapi.model.Lembrete;

public interface LembreteRepository extends JpaRepository<Lembrete, UUID>{
    
}
