package com.psychologyapi.psychologyapi.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.psychologyapi.psychologyapi.model.entity.LogAuditoria;

public interface LogAuditoriaRepository extends JpaRepository<LogAuditoria, UUID>{
    
}
